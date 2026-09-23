package com.distribuidora.backend.compras;

import com.distribuidora.backend.cadastros.Supplier;
import com.distribuidora.backend.cadastros.SupplierRepository;
import com.distribuidora.backend.comercial.SystemParameter;
import com.distribuidora.backend.comercial.SystemParameterRepository;
import com.distribuidora.backend.compras.PurchaseDtos.SuggestionResponse;
import com.distribuidora.backend.compras.PurchaseDtos.SuggestionRow;
import com.distribuidora.backend.security.CurrentUser;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// O que comprar hoje, calculado na hora a partir do que o ERP ja sabe:
// estoque disponivel, o que ja esta a caminho, a venda real do periodo e o
// prazo de entrega. Nada fica gravado ate virar pedido.
@Service
public class PurchaseSuggestionService {

    static final String CUSTO_VER = "produtos.custo.ver";

    private static final String SQL = """
            WITH disponivel AS (
                SELECT b.product_id,
                       COALESCE(SUM(CASE WHEN l.expires_on IS NULL OR l.expires_on >= :today
                                         THEN b.qty_physical - b.qty_reserved - b.qty_blocked - b.qty_damaged
                                         ELSE 0 END), 0) AS available
                FROM stock_balances b
                LEFT JOIN lots l ON l.id = b.lot_id
                GROUP BY b.product_id
            ),
            -- venda real do periodo, pelo que saiu faturado
            vendas AS (
                SELECT i.product_id, SUM(i.quantity) AS qty
                FROM invoice_items i
                JOIN invoices n ON n.id = i.invoice_id
                WHERE n.status = 'EMITIDA' AND n.issued_at >= :since
                GROUP BY i.product_id
            ),
            -- o que ja foi comprado e ainda nao chegou
            caminho AS (
                SELECT i.product_id, SUM(i.qty_base - i.qty_received) AS qty
                FROM purchase_order_items i
                JOIN purchase_orders o ON o.id = i.purchase_order_id
                WHERE o.status IN ('AGUARDANDO_APROVACAO', 'APROVADO', 'RECEBIDO_PARCIAL')
                GROUP BY i.product_id
            )
            SELECT p.id, p.sku, p.name, p.base_unit, p.min_stock, p.max_stock, p.lead_time_days,
                   p.main_supplier_id, p.average_cost, p.cost_price,
                   COALESCE(d.available, 0) AS available,
                   COALESCE(c.qty, 0)       AS incoming,
                   COALESCE(v.qty, 0)       AS sold,
                   (SELECT ps.supplier_id FROM product_suppliers ps WHERE ps.product_id = p.id LIMIT 1) AS any_supplier,
                   (SELECT ps.last_cost FROM product_suppliers ps
                     WHERE ps.product_id = p.id
                       AND ps.supplier_id = COALESCE(p.main_supplier_id,
                             (SELECT ps2.supplier_id FROM product_suppliers ps2 WHERE ps2.product_id = p.id LIMIT 1))
                   ) AS last_cost
            FROM products p
            LEFT JOIN disponivel d ON d.product_id = p.id
            LEFT JOIN vendas v     ON v.product_id = p.id
            LEFT JOIN caminho c    ON c.product_id = p.id
            WHERE p.active = TRUE
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final SupplierRepository supplierRepository;
    private final SystemParameterRepository parameterRepository;
    private final CurrentUser currentUser;
    private final Clock clock;

    public PurchaseSuggestionService(NamedParameterJdbcTemplate jdbc, SupplierRepository supplierRepository,
                                     SystemParameterRepository parameterRepository, CurrentUser currentUser,
                                     Clock clock) {
        this.jdbc = jdbc;
        this.supplierRepository = supplierRepository;
        this.parameterRepository = parameterRepository;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SuggestionResponse suggest(Integer salesWindowDays, Long supplierId) {
        int window = salesWindowDays == null || salesWindowDays <= 0 ? 60 : salesWindowDays;
        int coverage = coverageDays();
        LocalDate today = LocalDate.now(clock);
        Map<Long, Supplier> suppliers = supplierRepository.findAll().stream()
                .collect(Collectors.toMap(Supplier::getId, Function.identity()));
        boolean seesCost = currentUser.can(CUSTO_VER);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("today", java.sql.Date.valueOf(today))
                .addValue("since", java.sql.Timestamp.from(
                        today.minusDays(window).atStartOfDay(clock.getZone()).toInstant()));

        List<SuggestionRow> rows = jdbc.query(SQL, params, (rs, i) -> {
            BigDecimal available = rs.getBigDecimal("available");
            BigDecimal incoming = rs.getBigDecimal("incoming");
            BigDecimal minStock = rs.getBigDecimal("min_stock");
            BigDecimal maxStock = rs.getBigDecimal("max_stock");
            BigDecimal sold = rs.getBigDecimal("sold");
            Long mainSupplier = (Long) rs.getObject("main_supplier_id");
            Long anySupplier = (Long) rs.getObject("any_supplier");
            Long chosen = mainSupplier != null ? mainSupplier : anySupplier;
            Supplier supplier = chosen == null ? null : suppliers.get(chosen);
            Integer productLead = (Integer) rs.getObject("lead_time_days");
            Integer leadTime = productLead != null ? productLead
                    : supplier != null ? supplier.getLeadTimeDays() : null;

            BigDecimal daily = sold.divide(BigDecimal.valueOf(window), 4, RoundingMode.HALF_UP);
            Need need = evaluate(available, incoming, minStock, maxStock, daily,
                    leadTime == null ? 0 : leadTime, coverage);
            if (need == null) {
                return null;
            }
            BigDecimal position = available.add(incoming);
            BigDecimal coverageLeft = daily.signum() == 0 ? null
                    : position.divide(daily, 1, RoundingMode.HALF_UP);
            BigDecimal lastCost = rs.getBigDecimal("last_cost");
            BigDecimal averageCost = rs.getBigDecimal("average_cost");
            if (lastCost == null) {
                lastCost = rs.getBigDecimal("cost_price");
            }
            return new SuggestionRow(rs.getLong("id"), rs.getString("sku"), rs.getString("name"),
                    rs.getString("base_unit"), available, incoming, minStock, maxStock, daily, coverageLeft,
                    leadTime, need.quantity(), need.reason(), chosen,
                    supplier == null ? null : supplier.displayName(),
                    seesCost ? lastCost : null, seesCost ? averageCost : null);
        });

        List<SuggestionRow> result = rows.stream()
                .filter(java.util.Objects::nonNull)
                .filter(r -> supplierId == null || supplierId.equals(r.supplierId()))
                .sorted(Comparator.comparing((SuggestionRow r) -> r.coverageDays() == null
                        ? BigDecimal.valueOf(9999) : r.coverageDays()).thenComparing(SuggestionRow::name))
                .toList();
        return new SuggestionResponse(coverage, window, result);
    }

    record Need(BigDecimal quantity, String reason) {
    }

    // Regra: repor ate cobrir o prazo de entrega mais os dias de cobertura,
    // respeitando o minimo e o maximo do cadastro. Sem venda no periodo, so
    // repoe quem esta abaixo do minimo.
    static Need evaluate(BigDecimal available, BigDecimal incoming, BigDecimal minStock, BigDecimal maxStock,
                         BigDecimal dailySales, int leadTimeDays, int coverageDays) {
        BigDecimal position = available.add(incoming);
        BigDecimal min = minStock == null ? BigDecimal.ZERO : minStock;

        if (dailySales.signum() == 0) {
            if (min.signum() > 0 && position.compareTo(min) < 0) {
                return new Need(min.subtract(position), position.signum() == 0
                        ? "Sem estoque e abaixo do mínimo" : "Abaixo do estoque mínimo");
            }
            return null;
        }

        BigDecimal reorderPoint = dailySales.multiply(BigDecimal.valueOf(leadTimeDays)).max(min);
        if (position.compareTo(reorderPoint) >= 0) {
            return null;
        }
        BigDecimal target = dailySales.multiply(BigDecimal.valueOf(leadTimeDays + coverageDays)).max(min);
        if (maxStock != null && maxStock.signum() > 0) {
            target = target.min(maxStock);
        }
        BigDecimal quantity = target.subtract(position).setScale(3, RoundingMode.CEILING);
        if (quantity.signum() <= 0) {
            return null;
        }
        String reason = position.signum() <= 0 ? "Sem estoque disponível e com venda no período"
                : min.signum() > 0 && position.compareTo(min) < 0 ? "Abaixo do estoque mínimo"
                : "Cobertura menor que o prazo de entrega";
        return new Need(quantity, reason);
    }

    public int coverageDays() {
        return parameterRepository.findById(SystemParameter.PURCHASE_COVERAGE_DAYS)
                .map(p -> Integer.parseInt(p.getValue()))
                .orElse(15);
    }
}
