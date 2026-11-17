package io.github.projectopenubl.xhandler.documents;

import static io.github.projectopenubl.xhandler.Api.as;
import static io.github.projectopenubl.xhandler.Api.tenantOf;
import static io.github.projectopenubl.xhandler.engine.EngineStub.Reply;
import static io.github.projectopenubl.xhandler.engine.EngineStub.sendAnswer;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.projectopenubl.xhandler.company.TestCompanies;
import io.github.projectopenubl.xhandler.engine.EngineStub;
import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.rates.ExchangeRates;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** P4: voiding (RA, RC estado 3, RR), routes (third-party OSE, GRE), exchange rates, lost summary tickets. */
@QuarkusTest
@QuarkusTestResource(EngineStub.class)
class AllDocumentsTest {

    static final Duration WAIT = Duration.ofSeconds(25);

    @Inject
    TestCompanies companies;

    @Inject
    ExchangeRates rates;

    @Inject
    BoletaSummaryFallback fallback;

    @Inject
    TestDocuments docs;

    UUID tenant;
    UUID company;
    String base;

    @BeforeEach
    void setUp() {
        tenant = tenantOf("alice");
        company = companies.verified(tenant, "fake");
        base = "/api/v1/tenants/" + tenant;
    }

    @AfterEach
    void tearDown() {
        EngineStub.reset();
    }

    static Map<String, Object> document(String kind, Map<String, Object> spec, Object... options) {
        Map<String, Object> s = new HashMap<>(Map.of(
                "cliente", Map.of("tipo_documento_identidad", "6", "numero_documento_identidad", "12121212121", "nombre", "Cliente SAC"),
                "detalles", List.of(Map.of("descripcion", "Item", "cantidad", 1, "precio", 100))));
        s.putAll(spec);
        Map<String, Object> body = new HashMap<>(Map.of("document", Map.of("kind", kind, "spec", s)));
        for (int i = 0; i < options.length; i += 2) {
            body.put((String) options[i], options[i + 1]);
        }
        return body;
    }

    String emit(Map<String, Object> body) {
        return as("alice").body(body).post(base + "/companies/" + company + "/documents").then().statusCode(201).extract().path("id");
    }

    Map<String, Object> get(String id) {
        return as("alice").get(base + "/documents/" + id).then().statusCode(200).extract().as(Map.class);
    }

    void awaitStatus(String id, String status) {
        await().atMost(WAIT).until(() -> status.equals(get(id).get("status")));
    }

    Map<String, Object> voidRequest() {
        return Map.of("motivo", "Error en el precio", "not_delivered", true);
    }

    /** Summaries get a ticket; the ticket is accepted unless {@code rejectPrefix} matches. */
    void summariesByTicket(String rejectPrefix) {
        EngineStub.on("/v1/send", body -> body.contains("RA-") || body.contains("RC-") || body.contains("RR-")
                ? Reply.ok(sendAnswer("in_progress", null, null, "t-" + UUID.randomUUID()))
                : Reply.ok(sendAnswer("accepted", 0, null, null)));
        EngineStub.on("/v1/verify-ticket", body -> rejectPrefix != null && body.contains(rejectPrefix)
                ? Reply.ok(sendAnswer("rejected", 2375, null, null))
                : Reply.ok(sendAnswer("accepted", 0, null, null)));
    }

    @Test
    void a_factura_is_voided_by_a_comunicacion_de_baja() {
        summariesByTicket(null);
        String id = emit(document("Invoice", Map.of(), "delivered", false));
        awaitStatus(id, "accepted");
        String ra = as("alice").body(voidRequest()).post(base + "/documents/" + id + "/void").then().statusCode(202)
                .body("tipo_comprobante", equalTo("RA")).body("serie", startsWith("RA-")).extract().path("id");
        assertEquals("voiding", get(id).get("status"));
        awaitStatus(id, "voided");
        assertEquals("accepted", get(ra).get("status"));
        assertEquals(ra, get(id).get("voided_by_document_id"));
    }

    @Test
    void a_boleta_is_voided_by_a_resumen_diario_line_with_estado_3() {
        summariesByTicket(null);
        String id = emit(document("Invoice", Map.of("tipo_comprobante", "03"), "delivered", false));
        awaitStatus(id, "accepted");
        String rc = as("alice").body(voidRequest()).post(base + "/documents/" + id + "/void").then().statusCode(202)
                .body("tipo_comprobante", equalTo("RC")).extract().path("id");
        awaitStatus(id, "voided");
        assertTrue(EngineStub.requests("/v1/build", "\"tipo_operacion\":\"3\"").size() >= 1, "the RC line has estado 3");
        assertEquals("accepted", get(rc).get("status"));
    }

    @Test
    void a_rejected_voiding_puts_the_document_back() {
        summariesByTicket("RA-");
        String id = emit(document("Invoice", Map.of(), "delivered", false));
        awaitStatus(id, "accepted");
        as("alice").body(voidRequest()).post(base + "/documents/" + id + "/void").then().statusCode(202);
        await().atMost(WAIT).until(() -> "accepted".equals(get(id).get("status")) && get(id).get("voided_by_document_id") == null);
        as("alice").get(base + "/documents/" + id + "/events").then().body("message", org.hamcrest.Matchers.hasItem(containsString("not accepted")));
    }

    @Test
    void voiding_guards() {
        String delivered = emit(document("Invoice", Map.of(), "delivered", true));
        awaitStatus(delivered, "accepted");
        as("alice").body(voidRequest()).post(base + "/documents/" + delivered + "/void").then().statusCode(409)
                .body("kind", equalTo("delivered_use_credit_note"));

        String undelivered = emit(document("Invoice", Map.of(), "delivered", false));
        awaitStatus(undelivered, "accepted");
        as("alice").body(Map.of("motivo", "x")).post(base + "/documents/" + undelivered + "/void").then().statusCode(409)
                .body("kind", equalTo("delivered_use_credit_note"));

        String old = emit(document("Invoice", Map.of("fecha_emision", Deadlines.todayInLima().minusDays(8).toString()), "delivered", false));
        awaitStatus(old, "accepted");
        as("alice").body(voidRequest()).post(base + "/documents/" + old + "/void").then().statusCode(409)
                .body("kind", equalTo("void_window_passed"));

        String guia = emit(document("DespatchAdvice", Map.of(), "delivered", false));
        awaitStatus(guia, "accepted");
        as("alice").body(voidRequest()).post(base + "/documents/" + guia + "/void").then().statusCode(409)
                .body("kind", equalTo("void_in_sol_only"));
    }

    @Test
    void retenciones_are_reversed_by_a_resumen_de_reversiones() {
        summariesByTicket(null);
        String id = emit(document("Retention", Map.of(), "delivered", false));
        awaitStatus(id, "accepted");
        assertEquals("R001", get(id).get("serie"));
        as("alice").body(voidRequest()).post(base + "/documents/" + id + "/void").then().statusCode(422);
        as("alice").body(Map.of("motivo", "Reversión", "not_delivered", true, "reversal_communicated_at",
                        Deadlines.todayInLima().toString()))
                .post(base + "/documents/" + id + "/void").then().statusCode(202).body("tipo_comprobante", equalTo("RR"));
        awaitStatus(id, "voided");
    }

    @Test
    void a_company_can_send_through_a_third_party_ose() {
        as("alice").body(Map.of("submission_route", "ose", "ose", Map.of("name", "OSE Uno", "invoice_url", "http://127.0.0.1:1/ose/billService",
                        "username", "20123456789OSEUSER", "password", "secreto")))
                .put(base + "/companies/" + company + "/route").then().statusCode(200).body("submission_route", equalTo("ose"));
        String id = emit(document("Invoice", Map.of()));
        awaitStatus(id, "accepted");
        assertTrue(((String) get(id).get("serie_numero")).startsWith("F001"));
        String send = EngineStub.lastBody("/v1/send");
        assertTrue(send.contains("http://127.0.0.1:1/ose/billService"), send);
        assertTrue(send.contains("20123456789OSEUSER"), "sent with the OSE's credentials");
        // GRE always goes to SUNAT, whatever the route
        String guia = emit(document("DespatchAdvice", Map.of()));
        awaitStatus(guia, "accepted");
        assertTrue(!EngineStub.lastBody("/v1/send").contains("/ose/"), "GRE never through the OSE");
    }

    @Test
    void an_ose_obligated_company_cannot_send_directly() {
        as("olga").body(Map.of("ose_obligated", true))
                .put("/api/admin/v1/tenants/" + tenant + "/companies/" + company + "/ose-obligated").then().statusCode(200);
        as("alice").body(Map.of("submission_route", "sunat")).put(base + "/companies/" + company + "/route").then().statusCode(409)
                .body("kind", equalTo("ose_obligated"));
        UUID production = companies.verified(tenant, "production");
        as("alice").body(Map.of("username", "20000000001USER", "password", "x"))
                .put(base + "/companies/" + production + "/credentials/sol").then().statusCode(200);
        as("olga").body(Map.of("ose_obligated", true))
                .put("/api/admin/v1/tenants/" + tenant + "/companies/" + production + "/ose-obligated").then().statusCode(200);
        as("alice").body(document("Invoice", Map.of())).post(base + "/companies/" + production + "/documents").then().statusCode(422)
                .body("kind", equalTo("invalid_route"));
    }

    @Test
    void guias_are_sent_with_the_gre_api_client() {
        as("alice").body(Map.of("username", "20123456789GREUSER", "password", "x", "client_id", "gre-client", "client_secret", "gre-secret"))
                .put(base + "/companies/" + company + "/credentials/gre").then().statusCode(200);
        String id = emit(document("DespatchAdvice", Map.of()));
        awaitStatus(id, "accepted");
        assertTrue(EngineStub.lastBody("/v1/send").contains("\"client_id\":\"gre-client\""));
    }

    @Test
    void exchange_rates_are_imported_and_used_for_boletas_in_foreign_currency() throws Exception {
        EngineStub.FX_LINE.set("16/11/2026|3.700|3.750|");
        rates.run(new JobContext(UUID.randomUUID(), null, "platform.exchange-rates", 1,
                com.fasterxml.jackson.databind.node.NullNode.getInstance(), null, () -> false));
        as("alice").get("/api/v1/exchange-rates?date=2026-11-16").then().statusCode(200).body("venta", equalTo(3.75f));
        // 1180 USD ≈ S/ 4,425: the buyer must be identified
        Map<String, Object> anonymous = document("Invoice", Map.of("tipo_comprobante", "03", "moneda", "USD",
                "fecha_emision", "2026-11-16",
                "cliente", Map.of("tipo_documento_identidad", "0", "numero_documento_identidad", "-", "nombre", "Clientes varios")));
        as("alice").body(anonymous).post(base + "/companies/" + company + "/documents").then().statusCode(422)
                .body("detail", startsWith("2514"));
        Map<String, Object> identified = document("Invoice", Map.of("tipo_comprobante", "03", "moneda", "USD", "fecha_emision", "2026-11-16"));
        as("alice").body(identified).post(base + "/companies/" + company + "/documents").then().statusCode(201);
    }

    @Test
    void a_lost_summary_ticket_is_resolved_line_by_line() {
        String fecha = Deadlines.todayInLima().minusDays(6).toString();
        EngineStub.on("/v1/send", body -> body.contains("RC-")
                ? Reply.ok(sendAnswer("in_progress", null, null, "lost-ticket"))
                : body.contains("B001-") ? Reply.ok(sendAnswer("exception", 1079, "route_change", null))
                : Reply.ok(sendAnswer("accepted", 0, null, null)));
        // SUNAT says the ticket isn't ours / doesn't exist; each line is checked instead
        EngineStub.on("/v1/verify-ticket", body -> Reply.ok(sendAnswer("exception", 127, "ticket_lost", null)));
        String boleta = emit(document("Invoice", Map.of("tipo_comprobante", "03", "fecha_emision", fecha)));
        await().atMost(WAIT).until(() -> "route_change".equals(get(boleta).get("code_class")));
        fallback.run(new JobContext(UUID.randomUUID(), null, "documents.boleta-summary-fallback", 1,
                com.fasterxml.jackson.databind.node.NullNode.getInstance(), null, () -> false));
        String rc = (String) get(boleta).get("summary_document_id");
        // The lost ticket makes the RC ambiguous; skip the 2-minute wait before resolving
        await().atMost(WAIT).until(() -> "unknown".equals(get(rc).get("status")));
        docs.dueNow(tenant, UUID.fromString(rc));

        await().atMost(WAIT).until(() -> "accepted".equals(get(boleta).get("status")));
        Map<String, Object> summary = get(rc);
        assertEquals("exception", summary.get("status"));
        assertEquals("ticket_lost", summary.get("code_class"));
        assertTrue(EngineStub.requests().stream().anyMatch(r -> r.path().equals("/v1/validity")), "lines checked with validaCDPcriterios");
    }
}
