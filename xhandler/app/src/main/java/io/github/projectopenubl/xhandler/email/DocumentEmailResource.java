package io.github.projectopenubl.xhandler.email;

import io.github.projectopenubl.xhandler.api.ApiException;
import io.github.projectopenubl.xhandler.audit.Auditor;
import io.github.projectopenubl.xhandler.company.Company;
import io.github.projectopenubl.xhandler.documents.Document;
import io.github.projectopenubl.xhandler.documents.DocumentStatusChanged;
import io.github.projectopenubl.xhandler.jobs.Job;
import io.github.projectopenubl.xhandler.jobs.Jobs;
import io.github.projectopenubl.xhandler.tenancy.Actor;
import io.github.projectopenubl.xhandler.tenancy.Role;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.github.projectopenubl.xhandler.tenancy.TenantScope;
import jakarta.enterprise.event.Observes;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Emailing documents to buyers (DESIGN.md §11.6): on request (202 + job), or when SUNAT accepts. */
@Path("/api/v1/tenants/{tenantId}/documents/{documentId}/email")
public class DocumentEmailResource {

    public record EmailRequest(@NotEmpty @Size(max = 10) List<String> to, @Size(max = 10) List<String> cc, Boolean includeCdr) {}

    public record Queued(UUID jobId, String jobPrn) {}

    static final String EMAIL = "[^@\\s<>\"]+@[^@\\s<>\"]+\\.[A-Za-z]{2,}";

    private final TenantAccess access;
    private final Jobs jobs;
    private final Auditor auditor;
    private final EntityManager em;
    private final io.github.projectopenubl.xhandler.storage.FileStore files;
    private final com.fasterxml.jackson.databind.ObjectMapper json;

    public DocumentEmailResource(TenantAccess access, Jobs jobs, Auditor auditor, EntityManager em,
            io.github.projectopenubl.xhandler.storage.FileStore files, com.fasterxml.jackson.databind.ObjectMapper json) {
        this.files = files;
        this.json = json;
        this.access = access;
        this.jobs = jobs;
        this.auditor = auditor;
        this.em = em;
    }

    @POST
    @Transactional
    public Response send(@PathParam("tenantId") UUID tenantId, @PathParam("documentId") UUID documentId, @Valid EmailRequest request) {
        TenantScope scope = access.enter(tenantId, Role.OPERATOR);
        Document doc = Document.findById(documentId);
        if (doc == null) {
            throw ApiException.notFound();
        }
        List<String> cc = request.cc() == null ? List.of() : request.cc();
        for (String address : concat(request.to(), cc)) {
            if (!address.matches(EMAIL)) {
                throw ApiException.invalid("invalid_request", "Correo inválido: " + address, null);
            }
        }
        List<String> suppressed = request.to().stream().filter(a -> EmailSuppressions.suppressed(em, a)).toList();
        if (suppressed.size() == request.to().size()) {
            throw ApiException.invalid("email_suppressed", "Los destinatarios rebotaron antes y no se les envía correo.", String.join(", ", suppressed));
        }
        Job job = jobs.enqueue(scope, DocumentEmailJob.NAME, 3, Map.of("document_id", documentId.toString(), "to", request.to(), "cc", cc,
                "include_cdr", Boolean.TRUE.equals(request.includeCdr())), List.of(), null);
        auditor.record(scope, "document.email_requested", "document", documentId, Map.of("to", String.join(",", request.to())));
        return Response.status(202).entity(new Queued(job.id, "xh:job:" + job.id)).build();
    }

    /** Companies that ask for it email the buyer (the customer's email in the document) once SUNAT accepts. */
    void onAccepted(@Observes DocumentStatusChanged change) {
        Document doc = change.document();
        if (!Document.ACCEPTED.contains(doc.status) || Document.ACCEPTED.contains(change.previousStatus()) || doc.isSummary()) {
            return;
        }
        Company company = Company.findById(doc.companyId);
        if (company == null || !company.emailOnAccept) {
            return;
        }
        String address = customerEmail(doc);
        if (address == null || !address.matches(EMAIL) || EmailSuppressions.suppressed(em, address)) {
            return;
        }
        jobs.enqueue(new TenantScope(doc.tenantId, Actor.PLATFORM, Role.PLATFORM), DocumentEmailJob.NAME, 3,
                Map.of("document_id", doc.id.toString(), "to", List.of(address), "cc", List.of(), "include_cdr", false), List.of(),
                change.correlationId());
    }

    /** The buyer's email as the client gave it ({@code cliente.contacto.email} in the input). */
    private String customerEmail(Document doc) {
        Object raw = em.createNativeQuery("SELECT storage_key FROM xhandler.document_file WHERE document_id = :d AND kind = 'input'")
                .setParameter("d", doc.id).getResultStream().findFirst().orElse(null);
        if (raw == null) {
            return null;
        }
        try {
            var input = json.readTree(files.get(raw.toString()));
            var email = input.path("spec").path("cliente").path("contacto").path("email");
            return email.isMissingNode() || email.isNull() ? null : email.asText();
        } catch (java.io.IOException e) {
            return null;
        }
    }

    private static List<String> concat(List<String> a, List<String> b) {
        return java.util.stream.Stream.concat(a.stream(), b.stream()).toList();
    }
}
