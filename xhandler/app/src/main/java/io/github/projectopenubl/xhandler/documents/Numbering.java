package io.github.projectopenubl.xhandler.documents;

import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.company.Series;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Numbers are assigned by the service inside the emit transaction (DESIGN.md §8) with one atomic
 * update: a number is consumed when its document commits, and reissued if the transaction fails.
 * The unique index on {@code document} is the backstop. The series row stays locked until commit,
 * so emits on one series run one at a time (one series per POS terminal avoids contention).
 */
@ApplicationScoped
public class Numbering {

    private final EntityManager em;

    public Numbering(EntityManager em) {
        this.em = em;
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public long reserve(UUID companyId, String sunatEnv, String tipoComprobante, String serie) {
        List<?> next = em.createNativeQuery("""
                        UPDATE xhandler.series SET next_number = next_number + 1, updated_at = now()
                        WHERE company_id = :company AND sunat_env = :env AND tipo_comprobante = :tipo AND serie = :serie AND active
                        RETURNING next_number - 1""")
                .setParameter("company", companyId)
                .setParameter("env", sunatEnv)
                .setParameter("tipo", tipoComprobante)
                .setParameter("serie", serie)
                .getResultList();
        if (next.isEmpty()) {
            throw ApiException.invalid("series_not_found", "La serie " + serie + " no existe o no está activa para este tipo de comprobante.",
                    null);
        }
        return ((Number) next.get(0)).longValue();
    }

    /** RC/RA/RR numbers: {@code RC-YYYYMMDD-n}, per company, environment and Lima day (§8). */
    @Transactional(Transactional.TxType.MANDATORY)
    public int reserveDaily(UUID tenantId, UUID companyId, String sunatEnv, String kind, LocalDate day) {
        Object next = em.createNativeQuery("""
                        INSERT INTO xhandler.daily_counter (tenant_id, company_id, sunat_env, kind, day, next_number)
                        VALUES (:tenant, :company, :env, :kind, :day, 2)
                        ON CONFLICT (company_id, sunat_env, kind, day) DO UPDATE SET next_number = daily_counter.next_number + 1
                        RETURNING next_number - 1""")
                .setParameter("tenant", tenantId)
                .setParameter("company", companyId)
                .setParameter("env", sunatEnv)
                .setParameter("kind", kind)
                .setParameter("day", day)
                .getSingleResult();
        return ((Number) next).intValue();
    }

    /**
     * The default series for a document type. Notes pick the F or B family of the document they
     * affect ({@code prefix}: "F" or "B").
     */
    @Transactional(Transactional.TxType.MANDATORY)
    public String defaultSerie(UUID companyId, String sunatEnv, String tipoComprobante, String prefix) {
        List<Series> candidates = Series.list(
                "companyId = ?1 and sunatEnv = ?2 and tipoComprobante = ?3 and active order by isDefault desc, serie",
                companyId, sunatEnv, tipoComprobante);
        return candidates.stream()
                .filter(s -> prefix == null || s.serie.startsWith(prefix))
                .findFirst()
                .or(() -> candidates.stream().findFirst())
                .map(s -> s.serie)
                .orElseThrow(() -> ApiException.invalid("series_not_found",
                        "La empresa no tiene una serie activa para el tipo de comprobante " + tipoComprobante + ".", null));
    }
}
