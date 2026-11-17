package io.github.projectopenubl.xhandler.documents;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Turns an attempt's {@link Outcome} into the document's next state (DESIGN.md §9.2, §24.1). Pure:
 * no clock, no randomness, no database — the caller passes {@code now} and the jitter factor.
 *
 * <ul>
 *   <li>Definitive answers clear {@code next_attempt_at}; later attempts do nothing.
 *   <li>Transient failures retry with backoff 1m, 5m, 15m, 1h, then hourly (±20% jitter).
 *   <li>No hot loops: after 10 identical outcomes, or 24 hours without progress, the document gets
 *       {@code needs_attention} and keeps being retried every 6 hours (§9.2.4).
 * </ul>
 */
public final class OutcomePolicy {

    static final List<Duration> BACKOFF = List.of(
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofHours(1));
    static final List<Duration> TICKET_BACKOFF = List.of(
            Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofMinutes(1), Duration.ofMinutes(2), Duration.ofMinutes(5),
            Duration.ofMinutes(15));
    static final Duration NEEDS_ATTENTION_RETRY = Duration.ofHours(6);
    static final Duration NO_PROGRESS_LIMIT = Duration.ofHours(24);
    static final int IDENTICAL_LIMIT = 10;
    /** "Existe un documento igual en proceso" (0140) and similar: wait before resolving */
    static final Duration RESOLVE_WAIT = Duration.ofMinutes(2);

    /** The document as the attempt found it. */
    public record State(String status, String lastOutcome, int sameOutcomeCount, Instant progressAt, String holdReason) {}

    /**
     * The next state. {@code nextAttemptAt} null means no more automatic attempts. {@code
     * resolveNotBefore}: earliest time to resolve an ambiguous send (a stale call can't be in flight).
     */
    public record Decision(
            String status,
            Instant nextAttemptAt,
            String holdReason,
            String outcomeKey,
            int sameOutcomeCount,
            boolean progress) {

        public boolean definitive() {
            return nextAttemptAt == null;
        }
    }

    private OutcomePolicy() {}

    /**
     * @param jitter a factor in [0.8, 1.2]
     * @param resolveNotBefore for ambiguous sends: send start + SUNAT read timeout + margin
     */
    public static Decision decide(State state, Outcome outcome, Instant now, double jitter, Instant resolveNotBefore) {
        String key = outcome.kind() + ":" + (outcome.code() == null ? "-" : outcome.code());
        return switch (outcome.kind()) {
            case ACCEPTED -> finalState(observed(outcome) ? "accepted_with_observations" : "accepted", key);
            case REJECTED -> finalState("rejected", key);
            case VOIDED -> finalState("voided", key);
            case PERMANENT, LOCAL_ERROR, COLLISION, SUMMARY_RESOLVED -> finalState("exception", key);
            case ROUTE_CHANGE -> new Decision(state.status(), null, null, key, 1, true);
            case TICKET -> new Decision("in_progress", now.plus(TICKET_BACKOFF.get(0)), null, key, 1, true);
            case IN_PROGRESS -> retry(state, "in_progress", key, now, step(TICKET_BACKOFF, state, key), jitter);
            case TRANSIENT, NOT_DELIVERED -> retry(state, state.status(), key, now, step(BACKOFF, state, key), jitter);
            case AMBIGUOUS, ALREADY_FINAL, TICKET_LOST -> {
                Instant earliest = later(now.plus(RESOLVE_WAIT), resolveNotBefore);
                yield retryAt(state, "unknown", key, now, earliest);
            }
            case ALREADY_REGISTERED -> retryAt(state, "unknown", key, now, now.plus(RESOLVE_WAIT));
            case ENGINE_UNAVAILABLE -> "in_progress".equals(state.status())
                    ? retry(state, "in_progress", key, now, step(TICKET_BACKOFF, state, key), jitter)
                    : retryAt(state, "unknown", key, now, later(now.plus(RESOLVE_WAIT), resolveNotBefore));
            case UNCLASSIFIED -> {
                // Resolve first; if a resolving attempt still can't classify it, a person must look
                Decision next = retryAt(state, "unknown", key, now, later(now.plus(RESOLVE_WAIT), resolveNotBefore));
                if ("unknown".equals(state.status())) {
                    yield new Decision(next.status(), later(next.nextAttemptAt(), now.plus(NEEDS_ATTENTION_RETRY)),
                            "needs_attention", key, next.sameOutcomeCount(), false);
                }
                yield next;
            }
        };
    }

    /** Backoff step for the n-th identical outcome, hourly (last step) after the list. */
    public static Duration step(List<Duration> steps, State state, String key) {
        int n = key.equals(state.lastOutcome()) ? state.sameOutcomeCount() : 0;
        return steps.get(Math.min(n, steps.size() - 1));
    }

    private static Decision finalState(String status, String key) {
        return new Decision(status, null, null, key, 1, true);
    }

    private static Decision retry(State state, String status, String key, Instant now, Duration delay, double jitter) {
        long millis = Math.round(delay.toMillis() * jitter);
        return retryAt(state, status, key, now, now.plusMillis(millis));
    }

    private static Decision retryAt(State state, String status, String key, Instant now, Instant next) {
        int same = key.equals(state.lastOutcome()) ? state.sameOutcomeCount() + 1 : 1;
        boolean progress = !status.equals(state.status());
        Instant progressAt = progress ? now : state.progressAt();
        boolean stuck = same >= IDENTICAL_LIMIT || (progressAt != null && !now.isBefore(progressAt.plus(NO_PROGRESS_LIMIT)));
        String hold = "operator".equals(state.holdReason()) ? "operator" : null;
        if (stuck && hold == null) {
            return new Decision(status, later(next, now.plus(NEEDS_ATTENTION_RETRY)), "needs_attention", key, same, progress);
        }
        return new Decision(status, next, hold, key, same, progress);
    }

    private static boolean observed(Outcome outcome) {
        return (outcome.code() != null && outcome.code() >= 4000) || (outcome.notes() != null && !outcome.notes().isEmpty());
    }

    private static Instant later(Instant a, Instant b) {
        if (b == null) {
            return a;
        }
        return a.isAfter(b) ? a : b;
    }
}
