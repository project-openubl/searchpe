package io.github.projectopenubl.xhandler.reports;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The one row generator behind CSV, Excel, SIRE and legacy PLE (DESIGN.md §11.9): the sales
 * register from the company's documents, the purchases register from its receptions. Credit notes
 * carry negative amounts; voided documents keep their row with zero amounts (estado anulado).
 */
@ApplicationScoped
public class RegisterRows {

    /** Amount columns, in register order. */
    public static final List<String> AMOUNTS =
            List.of("exportacion", "gravada", "igv", "exonerada", "inafecta", "isc", "ivap_base", "ivap", "icbper", "otros");

    /**
     * One row of a register. {@code party} is the buyer (sales) or the supplier (purchases);
     * {@code affected*} the document a note modifies.
     */
    public record Row(
            LocalDate fecha,
            String tipo,
            String serie,
            long numero,
            String partyTipo,
            String partyNumero,
            String partyNombre,
            String moneda,
            BigDecimal tipoCambio,
            Map<String, BigDecimal> amounts,
            BigDecimal total,
            boolean voided,
            LocalDate affectedFecha,
            String affectedTipo,
            String affectedSerie,
            Long affectedNumero,
            boolean amountsMissing) {

        public String key() {
            return tipo + "-" + serie + "-" + numero;
        }

        public BigDecimal amount(String name) {
            return amounts.getOrDefault(name, BigDecimal.ZERO);
        }
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, String>> MAP = new TypeReference<>() {};

    private final EntityManager em;

    public RegisterRows(EntityManager em) {
        this.em = em;
    }

    /** With the tenant open. {@code period} is YYYYMM. */
    @Transactional(Transactional.TxType.MANDATORY)
    public List<Row> sales(UUID companyId, String sunatEnv, String period) {
        YearMonth month = month(period);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT d.fecha_emision, d.tipo_comprobante, d.serie, d.numero, d.customer_tipo, d.customer_numero, d.customer_nombre,
                               d.moneda,
                               coalesce(d.tipo_cambio, CASE WHEN d.moneda = 'USD' THEN (SELECT r.venta FROM xhandler.exchange_rate r
                                   WHERE r.day <= d.fecha_emision ORDER BY r.day DESC LIMIT 1) END),
                               cast(d.amounts AS text), d.total, d.status,
                               a.fecha_emision, a.tipo_comprobante, a.serie, a.numero
                        FROM xhandler.document d LEFT JOIN xhandler.document a ON a.id = d.affected_document_id
                        WHERE d.company_id = :c AND d.sunat_env = :env AND d.tipo_comprobante IN ('01', '03', '07', '08')
                          AND d.fecha_emision BETWEEN :from AND :to
                          AND d.status IN ('accepted', 'accepted_with_observations', 'voiding', 'voided')
                        ORDER BY d.fecha_emision, d.tipo_comprobante, d.serie, d.numero""")
                .setParameter("c", companyId).setParameter("env", sunatEnv)
                .setParameter("from", month.atDay(1)).setParameter("to", month.atEndOfMonth())
                .getResultList();
        List<Row> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            String tipo = (String) r[1];
            boolean voided = "voided".equals(r[11]);
            out.add(row(date(r[0]), tipo, (String) r[2], ((Number) r[3]).longValue(), (String) r[4], (String) r[5], (String) r[6],
                    (String) r[7], (BigDecimal) r[8], (String) r[9], (BigDecimal) r[10], voided,
                    date(r[12]), (String) r[13], (String) r[14], r[15] == null ? null : ((Number) r[15]).longValue()));
        }
        return out;
    }

    /** Receptions SUNAT didn't report as voided, rejected or nonexistent. */
    @Transactional(Transactional.TxType.MANDATORY)
    public List<Row> purchases(UUID companyId, String period) {
        YearMonth month = month(period);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT fecha_emision, tipo_comprobante, serie, numero, supplier_ruc, supplier_name, moneda, cast(amounts AS text), total,
                               affected_serie_numero, affected_tipo,
                               CASE WHEN moneda = 'USD' THEN (SELECT r.venta FROM xhandler.exchange_rate r
                                   WHERE r.day <= reception.fecha_emision ORDER BY r.day DESC LIMIT 1) END
                        FROM xhandler.reception
                        WHERE company_id = :c AND fecha_emision BETWEEN :from AND :to AND tipo_comprobante IN ('01', '03', '07', '08')
                          AND validity_state NOT IN ('not_found', 'voided', 'rejected')
                        ORDER BY fecha_emision, supplier_ruc, tipo_comprobante, serie, numero""")
                .setParameter("c", companyId).setParameter("from", month.atDay(1)).setParameter("to", month.atEndOfMonth())
                .getResultList();
        List<Row> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            String affected = (String) r[9];
            String affectedSerie = null;
            Long affectedNumero = null;
            if (affected != null && affected.contains("-")) {
                affectedSerie = affected.substring(0, affected.lastIndexOf('-'));
                try {
                    affectedNumero = Long.parseLong(affected.substring(affected.lastIndexOf('-') + 1));
                } catch (NumberFormatException e) {
                    affectedNumero = null;
                }
            }
            out.add(row(date(r[0]), (String) r[1], (String) r[2], ((Number) r[3]).longValue(), "6", (String) r[4], (String) r[5],
                    (String) r[6], (BigDecimal) r[11], (String) r[7], (BigDecimal) r[8], false, null, (String) r[10], affectedSerie,
                    affectedNumero));
        }
        return out;
    }

    private static Row row(LocalDate fecha, String tipo, String serie, long numero, String partyTipo, String partyNumero,
            String partyNombre, String moneda, BigDecimal tipoCambio, String amountsJson, BigDecimal total, boolean voided,
            LocalDate affectedFecha, String affectedTipo, String affectedSerie, Long affectedNumero) {
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        BigDecimal sign = "07".equals(tipo) ? BigDecimal.ONE.negate() : BigDecimal.ONE;
        boolean missing = !voided && (amountsJson == null || amountsJson.contains("\"_error\""));
        if (!voided && !missing) {
            try {
                for (Map.Entry<String, String> e : JSON.readValue(amountsJson, MAP).entrySet()) {
                    if (AMOUNTS.contains(e.getKey())) {
                        amounts.put(e.getKey(), new BigDecimal(e.getValue()).multiply(sign));
                    }
                }
            } catch (com.fasterxml.jackson.core.JsonProcessingException | NumberFormatException e) {
                throw new IllegalStateException("unreadable amounts", e);
            }
        }
        BigDecimal signedTotal = voided || total == null ? BigDecimal.ZERO : total.multiply(sign);
        return new Row(fecha, tipo, serie, numero, partyTipo, partyNumero, partyNombre, moneda == null ? "PEN" : moneda, tipoCambio,
                amounts, signedTotal, voided, affectedFecha, affectedTipo, affectedSerie, affectedNumero, missing);
    }

    /**
     * Documents of the period without a final answer yet (or stuck in exception): a register built
     * now would leave them out.
     */
    @Transactional(Transactional.TxType.MANDATORY)
    public long unresolved(UUID companyId, String sunatEnv, String period) {
        YearMonth month = month(period);
        return ((Number) em.createNativeQuery("""
                        SELECT count(*) FROM xhandler.document
                        WHERE company_id = :c AND sunat_env = :env AND tipo_comprobante IN ('01', '03', '07', '08')
                          AND fecha_emision BETWEEN :from AND :to AND status IN ('pending_send', 'in_progress', 'unknown', 'exception')""")
                .setParameter("c", companyId).setParameter("env", sunatEnv).setParameter("from", month.atDay(1))
                .setParameter("to", month.atEndOfMonth()).getSingleResult()).longValue();
    }

    public static YearMonth month(String period) {
        if (period == null || !period.matches("\\d{4}(0[1-9]|1[0-2])")) {
            throw io.github.projectopenubl.xhandler.api.ApiException.invalid("invalid_request", "El periodo debe tener el formato AAAAMM.",
                    null);
        }
        return YearMonth.of(Integer.parseInt(period.substring(0, 4)), Integer.parseInt(period.substring(4)));
    }

    private static LocalDate date(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof LocalDate d ? d : ((java.sql.Date) value).toLocalDate();
    }
}
