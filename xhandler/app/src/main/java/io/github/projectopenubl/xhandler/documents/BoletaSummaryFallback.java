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
            // One transaction per RC: a bad chunk can't block the rest of the group
            while (true) {
                Chunk chunk;
                try {
                    chunk = QuarkusTransaction.requiringNew().call(() ->
                            summarizeChunk(group.tenantId(), group.companyId(), group.sunatEnv(), group.fecha(), today, context.correlationId()));
                } catch (RuntimeException e) {
                    LOG.errorf(e, "resumen diario for company %s, day %s failed", group.companyId(), group.fecha());
                    failures.add(group.companyId() + "/" + group.fecha() + ": " + e.getMessage());
                    setAside(group.tenantId(), group.companyId(), group.sunatEnv(), group.fecha(), today, e.getMessage(), context.correlationId());
                    break;
                }
                if (chunk.lines() == 0) {
                    break;
                }
                summariesCreated++;
                if (chunk.lines() < Summaries.MAX_LINES) {
                    break;
                }
            }
        }
        return Map.of("groups", groups.size(), "summaries", summariesCreated, "failures", failures);
    }

    record Chunk(int lines) {}

    /** Lines a resumen diario may take: SUNAT can't have them and no attempt is in flight (§9.2.4). */
    static final String CANDIDATES = """
            companyId = ?1 and sunatEnv = ?2 and fechaEmision = ?3 and summaryDocumentId is null
            and origin <> 'ose_inbound'
            and (tipoComprobante = '03' or (tipoComprobante in ('07', '08') and serie like 'B%'))
            and (fechaEmision < ?4 or codeClass = 'route_change')
            and (status = 'pending_send' or (status = 'exception' and codeClass = 'route_change'))
            and (codeClass is null or codeClass <> 'summary_failed')
            and (holdReason is null or holdReason <> 'operator')
            order by id""";

    private Chunk summarizeChunk(UUID tenantId, UUID companyId, String sunatEnv, LocalDate fecha, LocalDate today, String correlationId) {
        TenantScope scope = access.enterAsPlatform(tenantId);
        // Lock order (§10.8 rule 4): company → daily counter → documents
        Company company = Company.findById(companyId, LockModeType.PESSIMISTIC_READ);
        int numero = summaries.reserveResumenDiario(company, sunatEnv);
        List<Document> boletas = Document.<Document>find(CANDIDATES, companyId, sunatEnv, fecha, today.minusDays(5))
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .withHint("jakarta.persistence.lock.timeout", -2)
                .page(0, Summaries.MAX_LINES)
                .list();
        boletas = boletas.stream().filter(d -> !attemptActive(d.id)).toList();
        if (boletas.isEmpty()) {
            // Rolls back: the reserved number is not consumed
            QuarkusTransaction.setRollbackOnly();
            return new Chunk(0);
        }
        Document rc = summaries.resumenDiario(scope, company, sunatEnv, fecha, boletas, "1", correlationId, Deadlines.todayInLima(), numero);
        Instant now = Instant.now();
        for (Document boleta : boletas) {
            boleta.summaryDocumentId = rc.id;
            boleta.nextAttemptAt = null;
            boleta.updatedAt = now;
            DocumentEvent.record(boleta, null, "moved into resumen diario " + rc.serieNumero(), "platform:worker", correlationId);
        }
        return new Chunk(boletas.size());
    }

    private boolean attemptActive(UUID documentId) {
        return !em.createNativeQuery("""
                        SELECT 1 FROM xhandler.job WHERE state IN ('waiting', 'running', 'canceling')
                        AND reserved_resources @> ARRAY[CAST(:resource AS text)]""")
                .setParameter("resource", "document:" + documentId)
                .getResultList().isEmpty();
    }

    /** A chunk whose RC couldn't be built: set its lines aside for a person instead of retrying every hour. */
    private void setAside(UUID tenantId, UUID companyId, String sunatEnv, LocalDate fecha, LocalDate today, String reason,
            String correlationId) {
        try {
            QuarkusTransaction.requiringNew().run(() -> {
                access.enterAsPlatform(tenantId);
                List<Document> boletas = Document.<Document>find(CANDIDATES, companyId, sunatEnv, fecha, today.minusDays(5))
                        .withLock(LockModeType.PESSIMISTIC_WRITE)
                        .page(0, Summaries.MAX_LINES)
                        .list();
                for (Document boleta : boletas) {
                    boleta.codeClass = "summary_failed";
                    boleta.holdReason = "needs_attention";
                    boleta.updatedAt = Instant.now();
                    DocumentEvent.record(boleta, null, "could not build its resumen diario: " + reason, "platform:worker", correlationId);
                }
            });
        } catch (RuntimeException e) {
            LOG.errorf(e, "could not set aside boletas of company %s, day %s", companyId, fecha);
        }
    }

    static LocalDate toDate(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        return ((java.sql.Date) value).toLocalDate();
    }
}
