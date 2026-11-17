package io.github.projectopenubl.xhandler.documents;

import io.github.projectopenubl.xhandler.engine.EngineModels;
import java.util.List;

/**
 * What an attempt learned, already classified (DESIGN.md §9.2.1, §9.2.3). Built from the engine's
 * answers by {@link #ofSend}; the attempt may refine it with follow-up calls (1033 digest check,
 * consult service) before {@link OutcomePolicy} turns it into the document's next state.
 */
public record Outcome(
        Kind kind, Integer code, String codeClass, String description, List<String> notes, String ticket, String cdrZip) {

    public enum Kind {
        /** CDR code 0 or ≥ 4000 (with observations when code ≥ 4000 or notes) */
        ACCEPTED,
        /** 2000–3999 */
        REJECTED,
        /** SUNAT says it is voided */
        VOIDED,
        /** Summary or GRE sent: a ticket to check */
        TICKET,
        /** Ticket answer 98 */
        IN_PROGRESS,
        /** SUNAT system failure (0100, 0109, 0130–0138...): retry with backoff, counts toward the breaker */
        TRANSIENT,
        /** The request never reached SUNAT: retry with backoff, counts toward the breaker */
        NOT_DELIVERED,
        /** SUNAT may have it: resolve before resending (§9.2.2) */
        AMBIGUOUS,
        /** 1033: registered before; needs the digest check */
        ALREADY_REGISTERED,
        /** 1032: already reported, voided or rejected; resolve, then definitive */
        ALREADY_FINAL,
        /** 0126/0127: ticket not ours or doesn't exist */
        TICKET_LOST,
        /** 1079: a boleta that can only go in a resumen diario */
        ROUTE_CHANGE,
        /** 1033 with different bytes: the number was already used */
        COLLISION,
        /** Credentials, package, content, references, not applicable: the user must act */
        PERMANENT,
        /** A code §9.2.3 doesn't list: resolve first, then needs_attention */
        UNCLASSIFIED,
        /** Our side failed before SUNAT (invalid document or request) */
        LOCAL_ERROR,
        /** The engine sidecar didn't answer: the send may or may not have happened; not SUNAT's fault */
        ENGINE_UNAVAILABLE
    }

    public static Outcome of(Kind kind, Integer code, String description) {
        return new Outcome(kind, code, null, description, List.of(), null, null);
    }

    public Outcome withCdr(String zip) {
        return new Outcome(kind, code, codeClass, description, notes, ticket, zip);
    }

    /** True when the call says nothing bad about the endpoint's health (for its breaker). */
    public boolean healthy() {
        return kind != Kind.TRANSIENT && kind != Kind.NOT_DELIVERED && !(kind == Kind.AMBIGUOUS && code == null);
    }

    /** Classifies the engine's answer to a send or a ticket check. */
    public static Outcome ofSend(EngineModels.SendResponse r) {
        List<String> notes = r.notes() == null ? List.of() : r.notes();
        Kind kind = switch (r.status()) {
            case "accepted" -> Kind.ACCEPTED;
            case "rejected" -> Kind.REJECTED;
            case "in_progress" -> r.ticket() != null ? Kind.TICKET : Kind.IN_PROGRESS;
            case "not_delivered" -> Kind.NOT_DELIVERED;
            case "exception" -> ofClass(r.codeClass());
            // xsender's Unknown: no usable answer (code null) or a code outside every range
            default -> r.code() == null ? Kind.AMBIGUOUS : Kind.UNCLASSIFIED;
        };
        return new Outcome(kind, r.code(), r.codeClass(), r.description(), notes, r.ticket(), r.cdrZip());
    }

    static Kind ofClass(String codeClass) {
        if (codeClass == null) {
            return Kind.UNCLASSIFIED;
        }
        return switch (codeClass) {
            case "transient_system" -> Kind.TRANSIENT;
            case "transient_constancia", "transient_in_process" -> Kind.AMBIGUOUS;
            case "already_registered" -> Kind.ALREADY_REGISTERED;
            case "already_final" -> Kind.ALREADY_FINAL;
            case "ticket_lost" -> Kind.TICKET_LOST;
            case "route_change" -> Kind.ROUTE_CHANGE;
            case "permanent_credentials", "permanent_package", "permanent_content", "permanent_references", "not_applicable" ->
                    Kind.PERMANENT;
            default -> Kind.UNCLASSIFIED;
        };
    }
}
