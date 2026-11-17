package io.github.projectopenubl.xhandler.documents;

import io.github.projectopenubl.xhandler.webhooks.WebhookEvents;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;

/**
 * Originals follow their RA/RC/RR (DESIGN.md §11.3): voided when it is accepted, back to their
 * previous status (and the user told) when it is not. Runs in the transaction that wrote the
 * voiding document's outcome, with its tenant open.
 */
@ApplicationScoped
public class VoidingOutcomes {

    private final Event<DocumentStatusChanged> statusChanged;
    private final WebhookEvents webhooks;

    public VoidingOutcomes(Event<DocumentStatusChanged> statusChanged, WebhookEvents webhooks) {
        this.statusChanged = statusChanged;
        this.webhooks = webhooks;
    }

    void onStatusChanged(@Observes DocumentStatusChanged change) {
        Document voiding = change.document();
        if (!voiding.isSummary() || !voiding.definitive() || "ticket_lost".equals(voiding.codeClass)) {
            // A lost ticket was resolved line by line already (AttemptHandler)
            return;
        }
        List<Document> originals = Document.<Document>find("voidedByDocumentId = ?1 and status = 'voiding' order by id", voiding.id)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .list();
        Instant now = Instant.now();
        if ("exception".equals(voiding.status)) {
            // Not a rejection: the voiding can still be fixed and resent, so the originals stay in
            // voiding (the user may cancel the void instead, POST …/void/cancel)
            for (Document original : originals) {
                DocumentEvent.record(original, voiding.sunatCode, "voiding " + voiding.serieNumero() + " needs attention: "
                        + voiding.sunatDescription, "platform:worker", change.correlationId());
            }
            return;
        }
        boolean accepted = Document.ACCEPTED.contains(voiding.status) || "voided".equals(voiding.status);
        for (Document original : originals) {
            String previous = original.status;
            if (accepted) {
                original.status = "voided";
                DocumentEvent.record(original, voiding.sunatCode, "voided by " + voiding.serieNumero(), "platform:worker",
                        change.correlationId());
            } else {
                original.status = original.statusBeforeVoiding == null ? "accepted" : original.statusBeforeVoiding;
                original.voidedByDocumentId = null;
                DocumentEvent.record(original, voiding.sunatCode, "voiding " + voiding.serieNumero() + " not accepted: "
                        + voiding.sunatDescription, "platform:worker", change.correlationId());
                webhooks.publish(original.tenantId, "document.void_rejected", "xh:document:" + original.id, WebhookEvents.DOCUMENT_SCHEMA,
                        WebhookEvents.documentData(original));
            }
            original.statusBeforeVoiding = null;
            original.updatedAt = now;
            statusChanged.fire(new DocumentStatusChanged(original, previous, change.correlationId()));
        }
    }
}
