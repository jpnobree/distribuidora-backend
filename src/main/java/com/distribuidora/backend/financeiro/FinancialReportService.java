package com.distribuidora.backend.financeiro;

import com.distribuidora.backend.financeiro.FinanceDtos.CashFlow;
import com.distribuidora.backend.financeiro.FinanceDtos.CashFlowBucket;
import com.distribuidora.backend.financeiro.FinanceDtos.Dre;
import com.distribuidora.backend.financeiro.FinanceDtos.DreLine;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Fluxo de caixa e DRE saem de consulta sobre o que ja existe (lancamentos,
// titulos e notas). Nao ha tabela de projecao nem fechamento mensal gravado:
// numero gravado e numero que envelhece sozinho.
@Service
public class FinancialReportService {

    public static final String VER = "financeiro.resultado";

    // Faixas do fluxo projetado, em dias a partir de hoje.
    private static final int[] HORIZONS = {0, 7, 15, 30, 60, 90};

    private static final String REALIZED_SQL =
            "SELECT COALESCE(SUM(CASE WHEN receivable_id IS NOT NULL THEN valor ELSE 0 END), 0) AS entrou, "
            + "       COALESCE(SUM(CASE WHEN payable_id IS NOT NULL THEN valor ELSE 0 END), 0) AS saiu "
            + "FROM (SELECT receivable_id, payable_id, "
            + "             (CASE WHEN type = 'BAIXA' THEN 1 ELSE -1 END) * (amount + interest - discount) AS valor "
            + "      FROM financial_transactions WHERE paid_on BETWEEN :from AND :to) t";

    private static final String OPEN_SQL =
            "SELECT due_date, (amount - paid_amount) AS saldo FROM %s WHERE status IN ('ABERTO', 'PARCIAL')";

    private static final String SALES_SQL =
            "SELECT COALESCE(SUM(subtotal), 0) AS bruta, COALESCE(SUM(discount_total), 0) AS descontos, "
            + "       COALESCE(SUM(total), 0) AS liquida, COALESCE(SUM(cost_total), 0) AS cmv, COUNT(*) AS notas "
            + "FROM invoices WHERE status = 'EMITIDA' AND issued_at >= :from AND issued_at < :to";

    // Compra de mercadoria nao entra como despesa: ela vira CMV quando a venda
    // acontece. So contam os titulos com categoria, que sao as despesas.
    private static final String EXPENSES_SQL =
            "SELECT expense_category, COALESCE(SUM(amount), 0) AS valor FROM payables "
            + "WHERE status <> 'CANCELADO' AND expense_category IS NOT NULL "
            + "  AND issue_date BETWEEN :from AND :to GROUP BY expense_category";

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public FinancialReportService(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    // ------------------------------------------------------ fluxo de caixa

    @Transactional(readOnly = true)
    public CashFlow cashFlow(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock);
        LocalDate start = from != null ? from : today.withDayOfMonth(1);
        LocalDate end = to != null ? to : today;

        Map<String, Object> realized = jdbc.queryForMap(REALIZED_SQL, new MapSqlParameterSource()
                .addValue("from", Date.valueOf(start))
                .addValue("to", Date.valueOf(end)));
        BigDecimal in = money(realized.get("entrou"));
        BigDecimal out = money(realized.get("saiu"));

        List<Object[]> receivables = openTitles("receivables");
        List<Object[]> payables = openTitles("payables");

        return new CashFlow(start, end, in, out, in.subtract(out),
                overdue(receivables, today), overdue(payables, today),
                buckets(receivables, payables, today));
    }

    private List<Object[]> openTitles(String table) {
        return jdbc.query(String.format(OPEN_SQL, table),
                (rs, i) -> new Object[]{rs.getDate("due_date").toLocalDate(), rs.getBigDecimal("saldo")});
    }

    private static BigDecimal overdue(List<Object[]> titles, LocalDate today) {
        return titles.stream()
                .filter(t -> ((LocalDate) t[0]).isBefore(today))
                .map(t -> (BigDecimal) t[1])
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Faixas em sequencia (hoje, ate 7, 8 a 15...) com o saldo acumulado ao
    // lado: e a pergunta "quanto sobra no dia 30" respondida linha a linha.
    // O vencido fica de fora das faixas e aparece em separado.
    static List<CashFlowBucket> buckets(List<Object[]> receivables, List<Object[]> payables, LocalDate today) {
        List<CashFlowBucket> result = new ArrayList<>();
        BigDecimal accumulated = BigDecimal.ZERO;
        LocalDate windowStart = today;
        for (int days : HORIZONS) {
            LocalDate end = today.plusDays(days);
            BigDecimal in = sumBetween(receivables, windowStart, end);
            BigDecimal out = sumBetween(payables, windowStart, end);
            BigDecimal net = in.subtract(out);
            accumulated = accumulated.add(net);
            result.add(new CashFlowBucket(label(days), windowStart, end, in, out, net, accumulated));
            windowStart = end.plusDays(1);
        }
        return result;
    }

    private static String label(int days) {
        return switch (days) {
            case 0 -> "Hoje";
            case 7 -> "Até 7 dias";
            case 15 -> "8 a 15 dias";
            case 30 -> "16 a 30 dias";
            case 60 -> "31 a 60 dias";
            default -> "61 a 90 dias";
        };
    }

    private static BigDecimal sumBetween(List<Object[]> titles, LocalDate from, LocalDate to) {
        return titles.stream()
                .filter(t -> {
                    LocalDate due = (LocalDate) t[0];
                    return !due.isBefore(from) && !due.isAfter(to);
                })
                .map(t -> (BigDecimal) t[1])
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // --------------------------------------------------------------- DRE

    @Transactional(readOnly = true)
    public Dre dre(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock);
        LocalDate start = from != null ? from : today.withDayOfMonth(1);
        LocalDate end = to != null ? to : today;

        Map<String, Object> sales = jdbc.queryForMap(SALES_SQL, new MapSqlParameterSource()
                .addValue("from", Timestamp.from(start.atStartOfDay(clock.getZone()).toInstant()))
                .addValue("to", Timestamp.from(end.plusDays(1).atStartOfDay(clock.getZone()).toInstant())));

        Map<ExpenseCategory, BigDecimal> byCategory = new LinkedHashMap<>();
        jdbc.query(EXPENSES_SQL, new MapSqlParameterSource()
                        .addValue("from", Date.valueOf(start))
                        .addValue("to", Date.valueOf(end)),
                (RowCallbackHandler) rs -> byCategory.put(ExpenseCategory.valueOf(rs.getString("expense_category")),
                        rs.getBigDecimal("valor")));

        BigDecimal gross = money(sales.get("bruta"));
        BigDecimal discounts = money(sales.get("descontos"));
        BigDecimal net = money(sales.get("liquida"));
        BigDecimal cmv = money(sales.get("cmv"));
        BigDecimal grossProfit = net.subtract(cmv);
        BigDecimal expenses = byCategory.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        long invoices = ((Number) sales.get("notas")).longValue();

        List<DreLine> lines = byCategory.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .map(e -> new DreLine(e.getKey().name(), e.getKey().getLabel(), e.getValue()))
                .toList();

        return new Dre(start, end, gross, discounts, net, cmv, grossProfit,
                net.signum() == 0 ? null
                        : grossProfit.multiply(BigDecimal.valueOf(100)).divide(net, 1, RoundingMode.HALF_UP),
                expenses, lines, grossProfit.subtract(expenses), invoices,
                invoices == 0 ? BigDecimal.ZERO
                        : net.divide(BigDecimal.valueOf(invoices), 2, RoundingMode.HALF_UP));
    }

    private static BigDecimal money(Object value) {
        return value == null ? BigDecimal.ZERO : ((BigDecimal) value).setScale(2, RoundingMode.HALF_UP);
    }
}
