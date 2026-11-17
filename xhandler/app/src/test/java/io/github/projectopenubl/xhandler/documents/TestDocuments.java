package io.github.projectopenubl.xhandler.documents;

import io.github.projectopenubl.xhandler.api.Ids;
import io.github.projectopenubl.xhandler.storage.FileStore;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Inserts signed, pending documents directly (what emit does), for job-system tests. Each XML
 * carries a unique {@code marker} so EngineStub scripts can tell documents apart.
 */
@ApplicationScoped
public class TestDocuments {

    private static final AtomicLong NUMERO = new AtomicLong(System.currentTimeMillis() % 1_000_000_000L);

    private final TenantAccess access;
    private final FileStore files;

    public TestDocuments(TenantAccess access, FileStore files) {
        this.access = access;
        this.files = files;
    }

    public record Created(UUID id, String marker) {}

    public Created pending(UUID tenantId, UUID companyId, String endpoint, Consumer<Document> customize) {
        String marker = "doc-" + UUID.randomUUID();
        return QuarkusTransaction.requiringNew().call(() -> {
            access.enterAsPlatform(tenantId);
            Document doc = new Document();
            doc.id = Ids.newId();
            doc.tenantId = tenantId;
            doc.companyId = companyId;
            doc.sunatEnv = "fake";
            doc.kind = "Invoice";
            doc.tipoComprobante = "01";
            doc.serie = "F001";
            doc.numero = NUMERO.incrementAndGet();
            doc.fechaEmision = Deadlines.todayInLima();
            doc.issueYear = (short) doc.fechaEmision.getYear();
            doc.deadlineAt = Deadlines.sendDeadline("01", "F001", doc.fechaEmision);
            doc.status = "pending_send";
            doc.endpoint = endpoint;
            doc.nextAttemptAt = Instant.now();
            doc.progressAt = Instant.now();
            doc.createdAt = Instant.now();
            doc.updatedAt = doc.createdAt;
            if (customize != null) {
                customize.accept(doc);
            }
            doc.issueYear = (short) doc.fechaEmision.getYear();
            doc.persist();
            String xml = "<Invoice><cbc:ID>" + doc.serieNumero() + "</cbc:ID><marker>" + marker + "</marker></Invoice>";
            FileStore.Stored stored = files.put(tenantId, xml.getBytes(StandardCharsets.UTF_8));
            DocumentFile.record(doc, "xml_signed", stored);
            return new Created(doc.id, marker);
        });
    }

    public Document get(UUID tenantId, UUID documentId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            access.enterAsPlatform(tenantId);
            Document doc = Document.findById(documentId);
            Document.getEntityManager().detach(doc);
            return doc;
        });
    }

    public void update(UUID tenantId, UUID documentId, Consumer<Document> change) {
        QuarkusTransaction.requiringNew().run(() -> {
            access.enterAsPlatform(tenantId);
            change.accept(Document.findById(documentId, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE));
        });
    }

    /** Makes a document due now (skips its backoff or lease). */
    public void dueNow(UUID tenantId, UUID documentId) {
        update(tenantId, documentId, d -> d.nextAttemptAt = Instant.now().minus(Duration.ofSeconds(1)));
    }

    /** Makes several documents due in one transaction. */
    public void dueNow(UUID tenantId, java.util.List<UUID> documentIds) {
        QuarkusTransaction.requiringNew().run(() -> {
            access.enterAsPlatform(tenantId);
            for (UUID id : documentIds.stream().sorted().toList()) {
                Document doc = Document.findById(id, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
                doc.nextAttemptAt = Instant.now().minus(Duration.ofSeconds(1));
            }
        });
    }

    public static LocalDate today() {
        return Deadlines.todayInLima();
    }
}
