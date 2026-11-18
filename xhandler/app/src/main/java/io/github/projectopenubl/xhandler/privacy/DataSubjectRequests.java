package io.github.projectopenubl.xhandler.privacy;

import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.api.Ids;
import io.github.projectopenubl.xhandler.api.Page;
import io.github.projectopenubl.xhandler.audit.Auditor;
import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.jobs.JobHandler;
import io.github.projectopenubl.xhandler.notifications.Notifications;
import io.github.projectopenubl.xhandler.status.StatusResource;
import io.github.projectopenubl.xhandler.tenancy.Role;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.github.projectopenubl.xhandler.tenancy.TenantScope;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Data-subject requests (Ley 29733 and DS 016-2024-JUS; DESIGN.md §16). The tenant is the
 * controller of its buyers' data; the platform is its processor. A request that reaches the
 * platform is registered by a platform admin and forwarded to the tenant at once (the processor's
 * 3-day duty); requests the tenant receives can be tracked here too. The tenant answers within the
 * law's term — 20 days for access, 10 for rectification, cancellation and opposition (calendar
 * days here: a conservative reading) — and gets the personal data the platform holds for that
 * person. Documents themselves can't be erased while tax law requires keeping them (§13).
 */
@Path("/api/v1/tenants/{tenantId}/data-subject-requests")
public class DataSubjectRequests {

    public record NewRequest(
            @NotBlank @Pattern(regexp = "[0-9A-Z]{1,2}") String subjectDocType,
            @NotBlank @Size(max = 20) String subjectDocNumber,
            @Size(max = 200) String subjectName,
            @NotBlank @Pattern(regexp = "access|rectification|cancellation|opposition") String kind,
            @Size(max = 2000) String details) {}

    public record Close(@NotBlank @Size(max = 2000) String resolution) {}

    public record DataSubjectRequestView(
            UUID id,
            String subjectDocType,
            String subjectDocNumber,
            String subjectName,
            @Schema(description = "access, rectification, cancellation u opposition.") String kind,
            @Schema(description = "tenant (registrada por la organización) o platform (llegó a la plataforma).") String channel,
            String details,
            String receivedAt,
            String forwardedAt,
            String responseDueAt,
            @Schema(description = "open o closed.") String status,
            String resolution,
            String closedAt) {}

    private final TenantAccess access;
    private final EntityManager em;
    private final Auditor auditor;
    private final Notifications notifications;

    public DataSubjectRequests(TenantAccess access, EntityManager em, Auditor auditor, Notifications notifications) {
        this.access = access;
        this.em = em;
        this.auditor = auditor;
        this.notifications = notifications;
    }

    static Duration term(String kind) {
        return "access".equals(kind) ? Duration.ofDays(20) : Duration.ofDays(10);
    }

    /** With the tenant open: records a request and tells the tenant. */
    @Transactional(Transactional.TxType.MANDATORY)
    public UUID register(TenantScope scope, NewRequest request, String channel) {
        UUID id = Ids.newId();
        Instant now = Instant.now();
        em.createNativeQuery("""
                        INSERT INTO xhandler.data_subject_request (id, tenant_id, subject_doc_type, subject_doc_number, subject_name, kind, channel,
                            details, received_at, forward_due_at, forwarded_at, response_due_at, created_by)
                        VALUES (:id, :t, :dt, :dn, :name, :kind, :channel, :details, :now, :forwardDue, :forwarded, :due, :by)""")
                .setParameter("id", id).setParameter("t", scope.tenantId()).setParameter("dt", request.subjectDocType())
                .setParameter("dn", request.subjectDocNumber()).setParameter("name", request.subjectName()).setParameter("kind", request.kind())
                .setParameter("channel", channel).setParameter("details", request.details()).setParameter("now", now)
                .setParameter("forwardDue", now.plus(Duration.ofDays(3))).setParameter("forwarded", now)
                .setParameter("due", now.plus(term(request.kind()))).setParameter("by", scope.actor().auditName()).executeUpdate();
        notifications.publish(scope.tenantId(), "privacy.request", "Solicitud de derechos de datos personales",
                "Se registró una solicitud de " + kindName(request.kind()) + " de datos personales. Respóndala antes del "
                        + lima(now.plus(term(request.kind()))) + ".", null);
        auditor.record(scope, "privacy.request_registered", "data_subject_request", id, Map.of("kind", request.kind(), "channel", channel));
        return id;
    }

    static String lima(Instant at) {
        return java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy").format(at.atZone(java.time.ZoneId.of("America/Lima")));
    }

    static String kindName(String kind) {
        return switch (kind) {
            case "access" -> "acceso";
            case "rectification" -> "rectificación";
            case "cancellation" -> "cancelación";
            default -> "oposición";
        };
    }

    @Operation(operationId = "createDataSubjectRequest")
    @POST
    @Transactional
    public DataSubjectRequestView create(@PathParam("tenantId") UUID tenantId, @Valid NewRequest request) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        return view(register(scope, request, "tenant"));
    }

    @Operation(operationId = "listDataSubjectRequests", summary = "Solicitudes de derechos de datos personales, las más recientes primero")
    @GET
    @Transactional
    public Page<DataSubjectRequestView> list(@PathParam("tenantId") UUID tenantId,
            @Schema(description = "open o closed; sin filtro, ambas.") @QueryParam("status") String status,
            @QueryParam("limit") Integer limit, @QueryParam("offset") Integer offset) {
        access.enter(tenantId, Role.ADMIN);
        int size = Page.limit(limit);
        int start = Page.offset(offset);
        @SuppressWarnings("unchecked")
        List<UUID> ids = em.createNativeQuery("""
                        SELECT id FROM xhandler.data_subject_request WHERE (cast(:s AS text) IS NULL OR status = :s)
                        ORDER BY received_at DESC, id DESC LIMIT :limit OFFSET :offset""")
                .setParameter("s", status).setParameter("limit", size).setParameter("offset", start).getResultList();
        return new Page<>(ids.stream().map(this::view).toList(), size, start);
    }

    /**
     * What the platform holds about the person: the documents issued to them (status and amounts,
     * no files), and the drafts that name them as buyer, deleted ones too until they are purged.
     */
    @Operation(operationId = "getDataSubjectPersonalData")
    @GET
    @Path("/{id}/personal-data")
    @Transactional
    public Map<String, Object> personalData(@PathParam("tenantId") UUID tenantId, @PathParam("id") UUID id) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        DataSubjectRequestView request = view(id);
        @SuppressWarnings("unchecked")
        List<Object[]> docs = em.createNativeQuery("""
                        SELECT d.id, c.ruc, d.tipo_comprobante, d.serie, d.numero, d.fecha_emision, d.customer_nombre, d.moneda, d.total, d.status
                        FROM xhandler.document d JOIN xhandler.company c ON c.id = d.company_id
                        WHERE d.customer_numero = :n AND d.customer_tipo = :t ORDER BY d.fecha_emision DESC LIMIT 5000""")
                .setParameter("n", request.subjectDocNumber()).setParameter("t", request.subjectDocType()).getResultList();
        @SuppressWarnings("unchecked")
        List<Object[]> drafts = em.createNativeQuery("""
                        SELECT dd.id, c.ruc, dd.kind, dd.tipo_comprobante, dd.serie, dd.spec->'cliente'->>'nombre', dd.status, dd.created_at,
                               dd.deleted_at
                        FROM xhandler.document_draft dd JOIN xhandler.company c ON c.id = dd.company_id
                        WHERE dd.spec->'cliente'->>'numero_documento_identidad' = :n AND dd.spec->'cliente'->>'tipo_documento_identidad' = :t
                        ORDER BY dd.created_at DESC LIMIT 5000""")
                .setParameter("n", request.subjectDocNumber()).setParameter("t", request.subjectDocType()).getResultList();
        auditor.record(scope, "privacy.personal_data_read", "data_subject_request", id,
                Map.of("documents", String.valueOf(docs.size()), "drafts", String.valueOf(drafts.size())));
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("request", request);
        answer.put("documents", docs.stream().map(r -> Map.of("id", r[0], "issuer_ruc", r[1], "tipo_comprobante", r[2],
                "serie_numero", r[3] + "-" + r[4], "fecha_emision", String.valueOf(r[5]), "nombre", String.valueOf(r[6]),
                "moneda", String.valueOf(r[7]), "total", String.valueOf(r[8]), "status", r[9])).toList());
        answer.put("drafts", drafts.stream().map(r -> {
            Map<String, Object> draft = new LinkedHashMap<>();
            draft.put("id", r[0]);
            draft.put("issuer_ruc", r[1]);
            draft.put("kind", r[2]);
            draft.put("tipo_comprobante", r[3]);
            draft.put("serie", r[4]);
            draft.put("nombre", r[5]);
            draft.put("status", r[6]);
            draft.put("created_at", String.valueOf(StatusResource.toInstant(r[7])));
            draft.put("deleted_at", r[8] == null ? null : String.valueOf(StatusResource.toInstant(r[8])));
            return draft;
        }).toList());
        return answer;
    }

    @Operation(operationId = "closeDataSubjectRequest")
    @POST
    @Path("/{id}/close")
    @Transactional
    public DataSubjectRequestView close(@PathParam("tenantId") UUID tenantId, @PathParam("id") UUID id, @Valid Close request) {
        TenantScope scope = access.enter(tenantId, Role.ADMIN);
        int closed = em.createNativeQuery("""
                        UPDATE xhandler.data_subject_request SET status = 'closed', resolution = :r, closed_at = now(), closed_by = :by
                        WHERE id = :id AND status = 'open'""")
                .setParameter("r", request.resolution()).setParameter("by", scope.actor().auditName()).setParameter("id", id).executeUpdate();
        if (closed == 0) {
            throw ApiException.notFound();
        }
        auditor.record(scope, "privacy.request_closed", "data_subject_request", id, Map.of());
        return view(id);
    }

    DataSubjectRequestView view(UUID id) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT id, subject_doc_type, subject_doc_number, subject_name, kind, channel, details, received_at, forwarded_at,
                               response_due_at, status, resolution, closed_at
                        FROM xhandler.data_subject_request WHERE id = :id""").setParameter("id", id).getResultList();
        if (rows.isEmpty()) {
            throw ApiException.notFound();
        }
        Object[] r = rows.get(0);
        return new DataSubjectRequestView((UUID) r[0], (String) r[1], (String) r[2], (String) r[3], (String) r[4], (String) r[5],
                (String) r[6], String.valueOf(StatusResource.toInstant(r[7])), r[8] == null ? null : String.valueOf(StatusResource.toInstant(r[8])),
                String.valueOf(StatusResource.toInstant(r[9])), (String) r[10], (String) r[11],
                r[12] == null ? null : String.valueOf(StatusResource.toInstant(r[12])));
    }

    /** Reminds tenants 2 days before the term; overdue requests are logged for the platform (daily). */
    @ApplicationScoped
    public static class Deadlines implements JobHandler {
        private final EntityManager em;
        private final TenantAccess access;
        private final Notifications notifications;

        public Deadlines(EntityManager em, TenantAccess access, Notifications notifications) {
            this.em = em;
            this.access = access;
            this.notifications = notifications;
        }

        @Override
        public String name() {
            return "privacy.deadlines";
        }

        @Override
        public Map<String, Object> run(JobContext context) {
            record Due(UUID tenant, UUID id, Instant dueAt) {}
            List<Due> due = QuarkusTransaction.requiringNew().call(() -> {
                @SuppressWarnings("unchecked")
                List<Object[]> rows = em.createNativeQuery("SELECT tenant_id, request_id, response_due_at FROM xhandler.privacy_requests_due(2)")
                        .getResultList();
                return rows.stream().map(r -> new Due((UUID) r[0], (UUID) r[1], StatusResource.toInstant(r[2]))).toList();
            });
            int reminded = 0;
            for (Due d : due) {
                if (d.dueAt().isBefore(Instant.now())) {
                    org.jboss.logging.Logger.getLogger("xhandler.audit.platform")
                            .warnf("data-subject request %s of tenant %s is overdue since %s", d.id(), d.tenant(), d.dueAt());
                }
                boolean sent = QuarkusTransaction.requiringNew().call(() -> {
                    access.enterAsPlatform(d.tenant());
                    int first = em.createNativeQuery(
                                    "UPDATE xhandler.data_subject_request SET reminded_at = now() WHERE id = :id AND reminded_at IS NULL AND status = 'open'")
                            .setParameter("id", d.id()).executeUpdate();
                    if (first == 1) {
                        notifications.publish(d.tenant(), "privacy.deadline", "Solicitud de datos personales por vencer",
                                "Una solicitud de derechos de datos personales vence el " + lima(d.dueAt()) + ".", null);
                    }
                    return first == 1;
                });
                if (sent) {
                    reminded++;
                }
            }
            return Map.of("due", due.size(), "reminded", reminded);
        }
    }
}
