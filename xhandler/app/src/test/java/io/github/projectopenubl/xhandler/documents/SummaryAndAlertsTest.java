package io.github.projectopenubl.xhandler.documents;

import static io.github.projectopenubl.xhandler.Api.as;
import static io.github.projectopenubl.xhandler.Api.tenantOf;
import static io.github.projectopenubl.xhandler.engine.EngineStub.Reply;
import static io.github.projectopenubl.xhandler.engine.EngineStub.sendAnswer;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.projectopenubl.xhandler.company.TestCompanies;
import io.github.projectopenubl.xhandler.engine.EngineStub;
import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Boleta D+5 → resumen diario fallback (§9.2.4) and deadline alerts. */
@QuarkusTest
@QuarkusTestResource(EngineStub.class)
class SummaryAndAlertsTest {

    static final Duration WAIT = Duration.ofSeconds(25);

    @Inject
    TestCompanies companies;

    @Inject
    TestDocuments docs;

    @Inject
    BoletaSummaryFallback fallback;

    @Inject
    DeadlineAlerts alerts;

    @Inject
    TenantAccess access;

    @AfterEach
    void tearDown() {
        EngineStub.reset();
    }

    static JobContext context(String name) {
        return new JobContext(UUID.randomUUID(), null, name, 1, com.fasterxml.jackson.databind.node.NullNode.getInstance(), "test",
                () -> false);
    }

    @Test
    void a_boleta_past_d_plus_5_goes_by_resumen_diario_and_follows_its_outcome() {
        UUID tenant = tenantOf("alice");
        UUID company = companies.verified(tenant, "fake");
        String base = "/api/v1/tenants/" + tenant;
        String fecha = Deadlines.todayInLima().minusDays(6).toString();
        // SUNAT says the boleta can only go in a resumen diario (1079)
        EngineStub.on("/v1/send", body -> {
            if (body.contains("RC-")) {
                return Reply.ok(sendAnswer("in_progress", null, null, "ticket-rc"));
            }
            if (body.contains("B001-")) {
                return Reply.ok(sendAnswer("exception", 1079, "route_change", null));
            }
            return Reply.ok(sendAnswer("accepted", 0, null, null));
        });
        Map<String, Object> boleta = Map.of("document", Map.of("kind", "Invoice", "spec", Map.of(
                "tipo_comprobante", "03", "fecha_emision", fecha,
                "cliente", Map.of("tipo_documento_identidad", "1", "numero_documento_identidad", "12345678", "nombre", "Ana"),
                "detalles", List.of(Map.of("descripcion", "Item", "cantidad", 1, "precio", 50)))));
        String id = as("alice").body(boleta).post(base + "/companies/" + company + "/documents").then().statusCode(201)
                .extract().path("id");
        await().atMost(WAIT).until(() -> "route_change".equals(
                as("alice").get(base + "/documents/" + id).then().extract().path("code_class")));

        fallback.run(context("documents.boleta-summary-fallback"));
        String summaryId = as("alice").get(base + "/documents/" + id).then().statusCode(200).extract().path("summary_document_id");
        assertNotNull(summaryId, "the boleta moved into a resumen diario");
        as("alice").get(base + "/documents/" + summaryId).then().statusCode(200)
                .body("tipo_comprobante", org.hamcrest.Matchers.equalTo("RC"))
                .body("serie", org.hamcrest.Matchers.startsWith("RC-"));

        // The RC gets a ticket, the ticket is accepted, and the boleta follows
        await().atMost(WAIT).until(() -> "accepted".equals(as("alice").get(base + "/documents/" + id).then().extract().path("status")));
        String description = as("alice").get(base + "/documents/" + id).then().extract().path("sunat_description");
        assertTrue(description.contains("resumen diario"), description);
        assertEquals("accepted", as("alice").get(base + "/documents/" + summaryId).then().extract().path("status"));
    }

    @Test
    void documents_near_their_deadline_are_alerted_once() throws Exception {
        UUID tenant = tenantOf("alice");
        UUID company = companies.verified(tenant, "fake");
        try (WebhookReceiver receiver = new WebhookReceiver()) {
            as("alice").body(Map.of("url", receiver.url(), "events", List.of("document.delayed")))
                    .post("/api/v1/tenants/" + tenant + "/webhooks").then().statusCode(201);
            TestDocuments.Created doc = docs.pending(tenant, company, "t-" + UUID.randomUUID() + ":invoice", d -> {
                d.holdReason = "operator";
                d.deadlineAt = Instant.now().plus(Duration.ofHours(2));
            });
            alerts.run(context("documents.deadline-alerts"));
            alerts.run(context("documents.deadline-alerts"));
            Document alerted = docs.get(tenant, doc.id());
            assertNotNull(alerted.deadlineAlertedAt);
            await().atMost(WAIT).until(() -> receiver.types().contains("document.delayed"));
            Thread.sleep(500);
            assertEquals(1, receiver.received().stream().filter(r -> r.body().contains(doc.id().toString())).count(), "alerted once");
            java.util.List<String> ids = as("alice").get("/api/v1/tenants/" + tenant + "/webhooks").then().extract().path("id");
            ids.forEach(w -> as("alice").delete("/api/v1/tenants/" + tenant + "/webhooks/" + w).then().statusCode(204));
        }
    }
}
