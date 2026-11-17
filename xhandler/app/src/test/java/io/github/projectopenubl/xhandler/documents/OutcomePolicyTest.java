package io.github.projectopenubl.xhandler.documents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.projectopenubl.xhandler.engine.EngineModels;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** DESIGN.md §9.2: what each outcome does to the document. */
class OutcomePolicyTest {

    static final Instant NOW = Instant.parse("2027-03-01T15:00:00Z");

    static OutcomePolicy.State fresh(String status) {
        return new OutcomePolicy.State(status, null, 0, NOW.minusSeconds(60), null);
    }

    static Outcome send(String status, Integer code, String codeClass, List<String> notes) {
        return Outcome.ofSend(new EngineModels.SendResponse(status, code, codeClass, "d", notes, null, null));
    }

    @Test
    void definitive_answers() {
        assertEquals("accepted", OutcomePolicy.decide(fresh("pending_send"), send("accepted", 0, null, List.of()), NOW, 1, null).status());
        var observed = OutcomePolicy.decide(fresh("pending_send"), send("accepted", 4252, null, List.of()), NOW, 1, null);
        assertEquals("accepted_with_observations", observed.status());
        assertNull(observed.nextAttemptAt());
        assertEquals("accepted_with_observations",
                OutcomePolicy.decide(fresh("pending_send"), send("accepted", 0, null, List.of("4252 - nota")), NOW, 1, null).status());
        assertEquals("rejected", OutcomePolicy.decide(fresh("pending_send"), send("rejected", 2335, null, List.of()), NOW, 1, null).status());
        var permanent = OutcomePolicy.decide(fresh("pending_send"), send("exception", 102, "permanent_credentials", List.of()), NOW, 1, null);
        assertEquals("exception", permanent.status());
        assertTrue(permanent.definitive());
    }

    @Test
    void transient_errors_back_off_1m_5m_15m_then_hourly() {
        Outcome transientError = send("exception", 109, "transient_system", List.of());
        OutcomePolicy.State state = fresh("pending_send");
        List<Duration> expected = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofHours(1),
                Duration.ofHours(1));
        for (Duration delay : expected) {
            var decision = OutcomePolicy.decide(state, transientError, NOW, 1.0, null);
            assertEquals("pending_send", decision.status());
            assertEquals(NOW.plus(delay), decision.nextAttemptAt());
            state = new OutcomePolicy.State(decision.status(), decision.outcomeKey(), decision.sameOutcomeCount(), state.progressAt(), null);
        }
    }

    @Test
    void jitter_is_applied() {
        var decision = OutcomePolicy.decide(fresh("pending_send"), send("not_delivered", null, null, List.of()), NOW, 1.2, null);
        assertEquals(NOW.plusSeconds(72), decision.nextAttemptAt());
    }

    @Test
    void ten_identical_failures_need_attention_and_retry_every_six_hours() {
        OutcomePolicy.State state = new OutcomePolicy.State("pending_send", "NOT_DELIVERED:-", 9, NOW.minusSeconds(60), null);
        var decision = OutcomePolicy.decide(state, send("not_delivered", null, null, List.of()), NOW, 1.0, null);
        assertEquals("needs_attention", decision.holdReason());
        assertEquals(NOW.plus(Duration.ofHours(6)), decision.nextAttemptAt());
    }

    @Test
    void a_day_without_progress_needs_attention() {
        OutcomePolicy.State state = new OutcomePolicy.State("pending_send", "TRANSIENT:109", 2, NOW.minus(Duration.ofHours(25)), null);
        var decision = OutcomePolicy.decide(state, send("not_delivered", null, null, List.of()), NOW, 1.0, null);
        assertEquals("needs_attention", decision.holdReason());
    }

    @Test
    void ambiguity_waits_for_any_stale_call_before_resolving() {
        Instant notBefore = NOW.plusSeconds(600);
        var decision = OutcomePolicy.decide(fresh("pending_send"), send("unknown", null, null, List.of()), NOW, 1.0, notBefore);
        assertEquals("unknown", decision.status());
        assertEquals(notBefore, decision.nextAttemptAt());
    }

    @Test
    void unclassified_codes_are_resolved_first_then_need_attention() {
        Outcome odd = send("exception", 1999, "unclassified", List.of());
        var first = OutcomePolicy.decide(fresh("pending_send"), odd, NOW, 1.0, null);
        assertEquals("unknown", first.status());
        assertNull(first.holdReason());
        var second = OutcomePolicy.decide(fresh("unknown"), odd, NOW, 1.0, null);
        assertEquals("needs_attention", second.holdReason());
    }

    @Test
    void codes_map_to_their_class() {
        assertEquals(Outcome.Kind.ALREADY_REGISTERED, send("exception", 1033, "already_registered", List.of()).kind());
        assertEquals(Outcome.Kind.ROUTE_CHANGE, send("exception", 1079, "route_change", List.of()).kind());
        assertEquals(Outcome.Kind.AMBIGUOUS, send("exception", 140, "transient_in_process", List.of()).kind());
        assertEquals(Outcome.Kind.TICKET_LOST, send("exception", 127, "ticket_lost", List.of()).kind());
        assertEquals(Outcome.Kind.PERMANENT, send("exception", 151, "permanent_package", List.of()).kind());
        assertEquals(Outcome.Kind.UNCLASSIFIED, send("unknown", 50, null, List.of()).kind());
        assertEquals(Outcome.Kind.AMBIGUOUS, send("unknown", null, null, List.of()).kind());
    }

    @Test
    void operator_holds_survive_retries() {
        OutcomePolicy.State held = new OutcomePolicy.State("pending_send", null, 0, NOW, "operator");
        assertEquals("operator", OutcomePolicy.decide(held, send("not_delivered", null, null, List.of()), NOW, 1.0, null).holdReason());
    }

    @Test
    void deadlines_follow_the_law_in_lima() {
        var day = java.time.LocalDate.of(2027, 3, 1);
        // Factura: D+3, end of day in Lima (UTC-5)
        assertEquals(Instant.parse("2027-03-05T04:59:59.999999999Z"), Deadlines.sendDeadline("01", "F001", day));
        assertEquals(Instant.parse("2027-03-07T04:59:59.999999999Z"), Deadlines.sendDeadline("03", "B001", day));
        assertEquals(Instant.parse("2027-03-07T04:59:59.999999999Z"), Deadlines.sendDeadline("07", "BC01", day));
        assertEquals(Instant.parse("2027-03-05T04:59:59.999999999Z"), Deadlines.sendDeadline("07", "FC01", day));
        assertTrue(Deadlines.boletaPastIndividualWindow(day, day.plusDays(6)));
    }
}
