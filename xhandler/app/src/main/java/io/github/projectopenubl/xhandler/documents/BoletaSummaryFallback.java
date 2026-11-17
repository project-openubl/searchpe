package io.github.projectopenubl.xhandler.documents;

import io.github.projectopenubl.xhandler.company.Company;
import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.jobs.JobHandler;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.github.projectopenubl.xhandler.tenancy.TenantScope;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * Boletas past D+5 go by resumen diario (DESIGN.md §9.2.4): every hour, boletas (and their notes)
 * still not accepted when D+5 passed in Lima, or answered 1079, are grouped by issue day into an RC
 * (estado 1) sent before D+7. Each boleta gets {@code summary_document_id} and stops being sent on
 * its own; it follows the RC's outcome ({@link SummaryOutcomes}).
 */
@ApplicationScoped
public class BoletaSummaryFallback implements JobHandler {

    private static final Logger LOG = Logger.getLogger(BoletaSummaryFallback.class);

    private final EntityManager em;
    private final TenantAccess access;
    private final Summaries summaries;

    public BoletaSummaryFallback(EntityManager em, TenantAccess access, Summaries summaries) {
        this.em = em;
        this.access = access;
        this.summaries = summaries;
    }

    @Override
    public String name() {
        return "documents.boleta-summary-fallback";
    }

    @Override
    public Map<String, Object> run(JobContext context) {
        record Group(UUID tenantId, UUID companyId, String sunatEnv, LocalDate fecha) {}
        LocalDate today = Deadlines.todayInLima();
        List<Group> groups = QuarkusTransaction.requiringNew().call(() -> {
            @SuppressWarnings("unchecked")
            List<Object[]> rows = em.createNativeQuery(
                            "SELECT tenant_id, company_id, sunat_env, fecha_emision FROM xhandler.boletas_needing_summary(:today)")
                    .setParameter("today", today)
                    .getResultList();
            return rows.stream().map(r -> new Group((UUID) r[0], (UUID) r[1], (String) r[2], toDate(r[3]))).toList();
        });
        int summariesCreated = 0;
        List<String> failures = new ArrayList<>();
        for (Group group : groups) {
            try {
                summariesCreated += QuarkusTransaction.requiringNew().call(() ->
                        summarize(group.tenantId(), group.companyId(), group.sunatEnv(), group.fecha(), today, context.correlationId()));
            } catch (RuntimeException e) {
                LOG.errorf(e, "resumen diario for company %s, day %s failed", group.companyId(), group.fecha());
                failures.add(group.companyId() + "/" + group.fecha() + ": " + e.getMessage());
            }
        }
        return Map.of("groups", groups.size(), "summaries", summariesCreated, "failures", failures);
    }

    private int summarize(UUID tenantId, UUID companyId, String sunatEnv, LocalDate fecha, LocalDate today, String correlationId) {
        TenantScope scope = access.enterAsPlatform(tenantId);
        Company company = Company.findById(companyId, LockModeType.PESSIMISTIC_READ);
        // Same filter as boletas_needing_summary, locked (documents in id order, §10.8 rule 4)
        List<Document> boletas = Document.<Document>find("""
                        companyId = ?1 and sunatEnv = ?2 and fechaEmision = ?3 and summaryDocumentId is null
                        and origin <> 'ose_inbound' and status in ('pending_send', 'unknown', 'exception')
                        and (tipoComprobante = '03' or (tipoComprobante in ('07', '08') and serie like 'B%'))
                        and (fechaEmision < ?4 or codeClass = 'route_change')
                        and (holdReason is null or holdReason <> 'operator')
                        order by id""", companyId, sunatEnv, fecha, today.minusDays(5))
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .list();
        int created = 0;
        for (int from = 0; from < boletas.size(); from += Summaries.MAX_LINES) {
            List<Document> chunk = boletas.subList(from, Math.min(boletas.size(), from + Summaries.MAX_LINES));
            Document rc = summaries.resumenDiario(scope, company, sunatEnv, fecha, chunk, "1", correlationId);
            Instant now = Instant.now();
            for (Document boleta : chunk) {
                boleta.summaryDocumentId = rc.id;
                boleta.nextAttemptAt = null;
                boleta.updatedAt = now;
                DocumentEvent.record(boleta, null, "moved into resumen diario " + rc.serieNumero(), "platform:worker", correlationId);
            }
            created++;
        }
        return created;
    }

    static LocalDate toDate(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        return ((java.sql.Date) value).toLocalDate();
    }
}
