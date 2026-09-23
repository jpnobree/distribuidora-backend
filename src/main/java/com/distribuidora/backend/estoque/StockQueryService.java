package com.distribuidora.backend.estoque;

import com.distribuidora.backend.estoque.StockDtos.BalanceDetail;
import com.distribuidora.backend.estoque.StockDtos.ExpiringLot;
import com.distribuidora.backend.estoque.StockDtos.MovementResponse;
import com.distribuidora.backend.estoque.StockDtos.PositionRow;
import com.distribuidora.backend.estoque.StockDtos.Summary;
import com.distribuidora.backend.estoque.StockEnums.MovementType;
import com.distribuidora.backend.model.Product;
import com.distribuidora.backend.repository.ProductRepository;
import com.distribuidora.backend.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

// Leituras agregadas no banco: a posicao de estoque soma saldos de todos os
// lotes/depositos sem carregar entidades na memoria.
@Service
public class StockQueryService {

    static final String CUSTO_VER = "produtos.custo.ver";

    private static final String POSITION_CTE = """
            WITH agg AS (
                SELECT p.id, p.sku, p.name, p.category, p.base_unit, p.storage_type, p.min_stock, p.max_stock,
                       p.average_cost,
                       COALESCE(SUM(b.qty_physical), 0) AS physical,
                       COALESCE(SUM(b.qty_reserved), 0) AS reserved,
                       COALESCE(SUM(b.qty_blocked), 0)  AS blocked,
                       COALESCE(SUM(b.qty_damaged), 0)  AS damaged,
                       -- vencido nao pode ser vendido: fica fora do disponivel
                       COALESCE(SUM(CASE WHEN l.expires_on < :today
                                         THEN b.qty_physical - b.qty_reserved - b.qty_blocked - b.qty_damaged
                                         ELSE 0 END), 0) AS expired
                FROM products p
                LEFT JOIN stock_balances b ON b.product_id = p.id
                     AND (CAST(:warehouseId AS BIGINT) IS NULL OR b.warehouse_id = :warehouseId)
                LEFT JOIN lots l ON l.id = b.lot_id
                WHERE p.active
                  AND (CAST(:search AS TEXT) IS NULL OR lower(p.name) LIKE :search OR lower(p.sku) LIKE :search)
                  AND (CAST(:category AS TEXT) IS NULL OR p.category = :category)
                GROUP BY p.id
            ), classified AS (
                SELECT agg.*,
                       physical - reserved - blocked - damaged - expired AS available,
                       CASE WHEN physical = 0 THEN 'ZERADO'
                            WHEN min_stock IS NOT NULL AND physical - reserved - blocked - damaged - expired < min_stock
                                 THEN 'ABAIXO_MINIMO'
                            WHEN max_stock IS NOT NULL AND physical > max_stock THEN 'EXCESSO'
                            ELSE 'OK' END AS situation
                FROM agg
            )
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final StockMovementRepository movementRepository;
    private final ProductRepository productRepository;
    private final LotRepository lotRepository;
    private final WarehouseRepository warehouseRepository;
    private final CurrentUser currentUser;
    private final Clock clock;

    public StockQueryService(NamedParameterJdbcTemplate jdbc, StockMovementRepository movementRepository,
                             ProductRepository productRepository, LotRepository lotRepository,
                             WarehouseRepository warehouseRepository, CurrentUser currentUser, Clock clock) {
        this.jdbc = jdbc;
        this.movementRepository = movementRepository;
        this.productRepository = productRepository;
        this.lotRepository = lotRepository;
        this.warehouseRepository = warehouseRepository;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<PositionRow> position(String search, String category, Long warehouseId, String situation,
                                      Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("warehouseId", warehouseId, Types.BIGINT)
                .addValue("today", Date.valueOf(LocalDate.now(clock)))
                .addValue("search", search == null || search.isBlank() ? null : "%" + search.trim().toLowerCase() + "%",
                        Types.VARCHAR)
                .addValue("category", category == null || category.isBlank() ? null : category, Types.VARCHAR)
                .addValue("situation", situation == null || situation.isBlank() ? null : situation, Types.VARCHAR)
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());
        String filter = " FROM classified WHERE (CAST(:situation AS TEXT) IS NULL OR situation = :situation)";
        Long total = jdbc.queryForObject(POSITION_CTE + "SELECT count(*)" + filter, params, Long.class);
        boolean seesCost = currentUser.can(CUSTO_VER);
        List<PositionRow> rows = jdbc.query(POSITION_CTE + "SELECT *" + filter
                + " ORDER BY name LIMIT :limit OFFSET :offset", params, (rs, i) -> {
            BigDecimal physical = rs.getBigDecimal("physical");
            BigDecimal cost = rs.getBigDecimal("average_cost");
            return new PositionRow(rs.getLong("id"), rs.getString("sku"), rs.getString("name"),
                    rs.getString("category"), rs.getString("base_unit"), rs.getString("storage_type"), physical,
                    rs.getBigDecimal("reserved"), rs.getBigDecimal("blocked"), rs.getBigDecimal("damaged"),
                    rs.getBigDecimal("expired"), rs.getBigDecimal("available"), rs.getBigDecimal("min_stock"), rs.getBigDecimal("max_stock"),
                    rs.getString("situation"),
                    seesCost ? cost : null,
                    seesCost && cost != null ? physical.multiply(cost).setScale(2, java.math.RoundingMode.HALF_UP) : null);
        });
        return new PageImpl<>(rows, pageable, total == null ? 0 : total);
    }

    // Disponível por produto, com a mesma conta da posição (sem reservado,
    // bloqueado, avariado nem vencido). É o que o catálogo público mostra.
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> availableFor(java.util.Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, BigDecimal> result = new java.util.HashMap<>();
        jdbc.query("""
                SELECT b.product_id,
                       SUM(CASE WHEN l.expires_on < :today THEN 0
                                ELSE b.qty_physical - b.qty_reserved - b.qty_blocked - b.qty_damaged END) AS available
                FROM stock_balances b
                LEFT JOIN lots l ON l.id = b.lot_id
                WHERE b.product_id IN (:ids)
                GROUP BY b.product_id
                """, new MapSqlParameterSource("ids", productIds)
                        .addValue("today", Date.valueOf(LocalDate.now(clock))),
                (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                        result.put(rs.getLong("product_id"), rs.getBigDecimal("available")));
        return result;
    }

    @Transactional(readOnly = true)
    public List<BalanceDetail> productBalances(Long productId) {
        LocalDate today = LocalDate.now(clock);
        return jdbc.query("""
                SELECT b.warehouse_id, w.name AS warehouse_name, b.lot_id, l.code AS lot_code, l.expires_on,
                       b.qty_physical, b.qty_reserved, b.qty_blocked, b.qty_damaged
                FROM stock_balances b
                JOIN warehouses w ON w.id = b.warehouse_id
                LEFT JOIN lots l ON l.id = b.lot_id
                WHERE b.product_id = :productId AND b.qty_physical > 0
                ORDER BY l.expires_on NULLS LAST, l.received_at, w.name
                """, new MapSqlParameterSource("productId", productId), (rs, i) -> {
            LocalDate expires = localDate(rs, "expires_on");
            BigDecimal physical = rs.getBigDecimal("qty_physical");
            BigDecimal reserved = rs.getBigDecimal("qty_reserved");
            BigDecimal blocked = rs.getBigDecimal("qty_blocked");
            BigDecimal damaged = rs.getBigDecimal("qty_damaged");
            return new BalanceDetail(rs.getLong("warehouse_id"), rs.getString("warehouse_name"),
                    (Long) rs.getObject("lot_id"), rs.getString("lot_code"), expires,
                    expires == null ? null : ChronoUnit.DAYS.between(today, expires),
                    physical, reserved, blocked, damaged, physical.subtract(reserved).subtract(blocked).subtract(damaged));
        });
    }

    // Lotes com saldo que vencem ate hoje + "days" (inclui os ja vencidos).
    @Transactional(readOnly = true)
    public List<ExpiringLot> expiringLots(int days, Long warehouseId) {
        LocalDate today = LocalDate.now(clock);
        boolean seesCost = currentUser.can(CUSTO_VER);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limitDate", Date.valueOf(today.plusDays(days)))
                .addValue("warehouseId", warehouseId, Types.BIGINT);
        return jdbc.query("""
                SELECT l.id AS lot_id, l.code, p.id AS product_id, p.name, p.sku, p.base_unit, p.average_cost,
                       l.expires_on, COALESCE(s.trade_name, s.legal_name) AS supplier_name,
                       SUM(b.qty_physical) AS physical,
                       SUM(b.qty_physical - b.qty_reserved - b.qty_blocked - b.qty_damaged) AS available
                FROM lots l
                JOIN stock_balances b ON b.lot_id = l.id
                JOIN products p ON p.id = l.product_id
                LEFT JOIN suppliers s ON s.id = l.supplier_id
                WHERE l.expires_on IS NOT NULL AND l.expires_on <= :limitDate
                  AND (CAST(:warehouseId AS BIGINT) IS NULL OR b.warehouse_id = :warehouseId)
                GROUP BY l.id, p.id, s.id
                HAVING SUM(b.qty_physical) > 0
                ORDER BY l.expires_on, p.name
                """, params, (rs, i) -> {
            LocalDate expires = localDate(rs, "expires_on");
            BigDecimal physical = rs.getBigDecimal("physical");
            BigDecimal cost = rs.getBigDecimal("average_cost");
            return new ExpiringLot(rs.getLong("lot_id"), rs.getString("code"), rs.getLong("product_id"),
                    rs.getString("name"), rs.getString("sku"), rs.getString("base_unit"), expires,
                    ChronoUnit.DAYS.between(today, expires), physical, rs.getBigDecimal("available"),
                    seesCost && cost != null ? physical.multiply(cost).setScale(2, java.math.RoundingMode.HALF_UP) : null,
                    rs.getString("supplier_name"));
        });
    }

    @Transactional(readOnly = true)
    public Summary summary(int windowDays) {
        LocalDate today = LocalDate.now(clock);
        boolean seesCost = currentUser.can(CUSTO_VER);
        MapSqlParameterSource none = new MapSqlParameterSource()
                .addValue("today", Date.valueOf(today))
                .addValue("warehouseId", null, Types.BIGINT)
                .addValue("search", null, Types.VARCHAR)
                .addValue("category", null, Types.VARCHAR);
        Map<String, Object> position = jdbc.queryForMap(POSITION_CTE + """
                SELECT COALESCE(SUM(physical * COALESCE(average_cost, 0)), 0) AS stock_value,
                       COUNT(*) FILTER (WHERE situation = 'ABAIXO_MINIMO') AS below_min,
                       COUNT(*) FILTER (WHERE situation = 'ZERADO' AND min_stock IS NOT NULL) AS out_of_stock,
                       COALESCE(SUM(physical) FILTER (WHERE base_unit = 'KG'), 0) AS total_kg,
                       COALESCE(SUM(available) FILTER (WHERE base_unit = 'KG'), 0) AS available_kg
                FROM classified
                """, none);

        MapSqlParameterSource dates = new MapSqlParameterSource()
                .addValue("today", Date.valueOf(today))
                .addValue("limitDate", Date.valueOf(today.plusDays(windowDays)))
                .addValue("monthStart", Timestamp.from(today.withDayOfMonth(1).atStartOfDay(clock.getZone()).toInstant()))
                .addValue("lossTypes", Arrays.stream(MovementType.values()).filter(MovementType::isLoss)
                        .map(Enum::name).toList());
        Map<String, Object> lots = jdbc.queryForMap("""
                WITH lot_qty AS (
                    SELECT l.id, l.expires_on, SUM(b.qty_physical) AS qty, MAX(p.average_cost) AS cost
                    FROM lots l JOIN stock_balances b ON b.lot_id = l.id JOIN products p ON p.id = l.product_id
                    WHERE l.expires_on IS NOT NULL
                    GROUP BY l.id HAVING SUM(b.qty_physical) > 0
                )
                SELECT COUNT(*) FILTER (WHERE expires_on >= :today AND expires_on <= :limitDate) AS expiring,
                       COALESCE(SUM(qty * COALESCE(cost, 0)) FILTER (WHERE expires_on >= :today AND expires_on <= :limitDate), 0) AS expiring_value,
                       COUNT(*) FILTER (WHERE expires_on < :today) AS expired,
                       COALESCE(SUM(qty * COALESCE(cost, 0)) FILTER (WHERE expires_on < :today), 0) AS expired_value,
                       (SELECT COALESCE(SUM(m.quantity * COALESCE(m.unit_cost, 0)), 0) FROM stock_movements m
                         WHERE m.type IN (:lossTypes) AND m.occurred_at >= :monthStart) AS losses
                FROM lot_qty
                """, dates);

        return new Summary(
                seesCost ? money(position.get("stock_value")) : null,
                ((Number) position.get("below_min")).longValue(),
                ((Number) position.get("out_of_stock")).longValue(),
                ((Number) lots.get("expiring")).longValue(),
                seesCost ? money(lots.get("expiring_value")) : null,
                ((Number) lots.get("expired")).longValue(),
                seesCost ? money(lots.get("expired_value")) : null,
                seesCost ? money(lots.get("losses")) : null,
                windowDays,
                (BigDecimal) position.get("total_kg"),
                (BigDecimal) position.get("available_kg"));
    }

    @Transactional(readOnly = true)
    public Page<MovementResponse> movements(Long productId, Long warehouseId, Long lotId, MovementType type,
                                            Instant from, Instant to, int page, int size) {
        Specification<StockMovement> spec = Specification.where(null);
        if (productId != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("productId"), productId));
        }
        if (warehouseId != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("warehouseId"), warehouseId));
        }
        if (lotId != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("lotId"), lotId));
        }
        if (type != null) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("type"), type));
        }
        if (from != null) {
            spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("occurredAt"), from));
        }
        if (to != null) {
            spec = spec.and((r, q, cb) -> cb.lessThan(r.get("occurredAt"), to));
        }
        Page<StockMovement> result = movementRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt", "id")));

        List<StockMovement> content = result.getContent();
        Map<Long, Product> products = productRepository.findAllById(
                content.stream().map(StockMovement::getProductId).distinct().toList()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, String> lots = lotRepository.findAllById(
                content.stream().map(StockMovement::getLotId).filter(Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(Lot::getId, Lot::getCode));
        Map<Long, String> warehouses = warehouseRepository.findAll().stream()
                .collect(Collectors.toMap(Warehouse::getId, Warehouse::getName));
        boolean seesCost = currentUser.can(CUSTO_VER);

        return result.map(m -> {
            Product p = products.get(m.getProductId());
            BigDecimal unitCost = seesCost ? m.getUnitCost() : null;
            return new MovementResponse(m.getId(), m.getOccurredAt(), m.getType(), m.getType().getLabel(),
                    m.getWarehouseId(), warehouses.get(m.getWarehouseId()), m.getProductId(),
                    p == null ? null : p.getName(), p == null ? null : p.getSku(), p == null ? null : p.getBaseUnit(),
                    m.getLotId(), lots.get(m.getLotId()), m.getQuantity(), unitCost,
                    unitCost == null ? null : m.getQuantity().multiply(unitCost).setScale(2, java.math.RoundingMode.HALF_UP),
                    m.getLossReason(), m.getLossReason() == null ? null : m.getLossReason().getLabel(),
                    m.getReason(), m.getDocument(), m.getUsername());
        });
    }

    private static BigDecimal money(Object value) {
        return value == null ? BigDecimal.ZERO
                : new BigDecimal(value.toString()).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static LocalDate localDate(ResultSet rs, String column) throws SQLException {
        Date date = rs.getDate(column);
        return date == null ? null : date.toLocalDate();
    }
}
