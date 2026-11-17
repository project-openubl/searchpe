package io.github.projectopenubl.xhandler.documents;

import io.github.projectopenubl.xhandler.api.Ids;
import io.github.projectopenubl.xhandler.storage.FileStore;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** A file of a document in storage: signed XML, CDR, PDF, input or enriched JSON. Immutable. */
@Entity
@org.hibernate.annotations.Immutable
@Table(name = "document_file")
public class DocumentFile extends PanacheEntityBase {

    @Id
    public UUID id;

    public UUID tenantId;
    public UUID documentId;
    /** xml_signed, cdr_zip, pdf, input, enriched */
    public String kind;
    public String storageKey;
    public String sha256;
    public long size;
    public Instant createdAt;

    public static DocumentFile record(Document document, String kind, FileStore.Stored stored) {
        DocumentFile file = new DocumentFile();
        file.id = Ids.newId();
        file.tenantId = document.tenantId;
        file.documentId = document.id;
        file.kind = kind;
        file.storageKey = stored.key();
        file.sha256 = stored.sha256();
        file.size = stored.size();
        file.createdAt = Instant.now();
        file.persist();
        return file;
    }

    public static Optional<DocumentFile> of(UUID documentId, String kind) {
        return find("documentId = ?1 and kind = ?2", documentId, kind).firstResultOptional();
    }
}
