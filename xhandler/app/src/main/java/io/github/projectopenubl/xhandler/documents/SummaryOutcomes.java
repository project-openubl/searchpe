package io.github.projectopenubl.xhandler.documents;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;

/**
 * Documents in a resumen diario follow its outcome (DESIGN.md §9.2.4). Runs in the transaction that
 * wrote the RC's outcome, with its tenant open.
 */
@ApplicationScoped
public class SummaryOutcomes {

    private final Event<DocumentStatusChanged> statusChanged;

    public SummaryOutcomes(Event<DocumentStatusChanged> statusChanged) {
        this.statusChanged = statusChanged;
    }

    void onStatusChanged(@Observes DocumentStatusChanged change) {
        Document rc = change.document();
        if (!"RC".equals(rc.tipoComprobante) || !rc.definitive() || "ticket_lost".equals(rc.codeClass)) {
            // A lost ticket was resolved line by line already (AttemptHandler)
            return;
        }
        List<Document> lines = Document.<Document>find("summaryDocumentId = ?1 order by id", rc.id)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .list();
        Instant now = Instant.now();
        for (Document line : lines) {
            if (line.definitive() && !"exception".equals(line.status)) {
                continue;
            }
            String previous = line.status;
            if (Document.ACCEPTED.contains(rc.status)) {
                line.status = rc.status;
                line.sunatCode = rc.sunatCode;
                line.sunatDescription = "Aceptado en el resumen diario " + rc.serieNumero();
                line.holdReason = null;
            } else {
                // The RC failed: the boletas need a person (retrying puts them in a new RC)
                line.status = "exception";
                line.sunatCode = rc.sunatCode;
                line.sunatDescription = "El resumen diario " + rc.serieNumero() + " no fue aceptado: " + rc.sunatDescription;
                line.holdReason = "needs_attention";
            }
            line.nextAttemptAt = null;
            line.progressAt = now;
            line.updatedAt = now;
            DocumentEvent.record(line, line.sunatCode, line.sunatDescription, "platform:worker", change.correlationId());
            if (!line.status.equals(previous)) {
                statusChanged.fire(new DocumentStatusChanged(line, previous, change.correlationId()));
            }
        }
    }
}
