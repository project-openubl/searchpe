package io.github.projectopenubl.xhandler.documents;

import io.github.projectopenubl.xhandler.api.Ids;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One entry of a document's timeline (DESIGN.md §7.1). Append-only. */
@Entity
@org.hibernate.annotations.Immutable
@Table(name = "document_event")
public class DocumentEvent extends PanacheEntityBase {

    @Id
    public UUID id;

    public Instant at;
    public UUID tenantId;
    public UUID documentId;
    public String status;
    public Integer code;
    public String message;
    public String actor;
    public String correlationId;

    public static DocumentEvent record(Document document, Integer code, String message, String actor, String correlationId) {
        DocumentEvent event = new DocumentEvent();
        event.id = Ids.newId();
        event.at = Instant.now();
        event.tenantId = document.tenantId;
        event.documentId = document.id;
        event.status = document.status;
        event.code = code;
        event.message = message;
        event.actor = actor;
        event.correlationId = correlationId;
        event.persist();
        return event;
    }
}
