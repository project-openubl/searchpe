package io.github.projectopenubl.xhandler.documents;

import static io.github.projectopenubl.xhandler.Api.as;
import static io.github.projectopenubl.xhandler.Api.tenantOf;
import static io.github.projectopenubl.xhandler.engine.EngineStub.Reply;
import static io.github.projectopenubl.xhandler.engine.EngineStub.sendAnswer;
import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.projectopenubl.xhandler.company.TestCompanies;
import io.github.projectopenubl.xhandler.engine.EngineStub;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Emit (DESIGN.md §11.1): numbering, idempotency, signing, storage, buyer links, then the send. */
@QuarkusTest
@QuarkusTestResource(EngineStub.class)
class EmitTest {

    static final Duration WAIT = Duration.ofSeconds(20);

    @Inject
    TestCompanies companies;

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

    static Map<String, Object> invoice(Map<String, Object> extra) {
        Map<String, Object> spec = new HashMap<>(Map.of(
                "cliente", Map.of("tipo_documento_identidad", "6", "numero_documento_identidad", "12121212121", "nombre", "Cliente SAC"),
                "detalles", List.of(Map.of("descripcion", "Item", "cantidad", 10, "precio", 100))));
        spec.putAll(extra);
        return Map.of("document", Map.of("kind", "Invoice", "spec", spec));
    }

    Response emit(Object body, String key) {
        var request = as("alice").body(body);
        if (key != null) {
            request = request.header("Idempotency-Key", key);
        }
        return request.post(base + "/companies/" + company + "/documents");
    }

    String status(String id) {
        return as("alice").get(base + "/documents/" + id).then().statusCode(200).extract().path("status");
    }

    @Test
    void emit_numbers_signs_and_stores_then_sunat_accepts() {
        Response emitted = emit(invoice(Map.of()), null);
        emitted.then().statusCode(201)
                .body("status", equalTo("pending_send"))
                .body("serie_numero", equalTo("F001-1"))
                .body("tipo_comprobante", equalTo("01"))
                .body("access_url", startsWith("http"))
                .body("qr", startsWith(""))
                .body("prn", startsWith("xh:document:"));
        String id = emitted.path("id");
        await().atMost(WAIT).until(() -> "accepted".equals(status(id)));

        // The signed XML and the CDR are downloadable; the timeline shows emit and acceptance
        as("alice").get(base + "/documents/" + id + "/files/xml").then().statusCode(200)
                .body(org.hamcrest.Matchers.containsString("F001-1"));
        as("alice").get(base + "/documents/" + id + "/files/cdr").then().statusCode(200);
        as("alice").get(base + "/documents/" + id + "/events").then().statusCode(200)
                .body("message", hasItem("emitted"));
        // The next emit gets the next number
        emit(invoice(Map.of()), null).then().statusCode(201).body("serie_numero", equalTo("F001-2"));
    }

    @Test
    void boletas_and_notes_pick_their_series() {
        Map<String, Object> boleta = invoice(Map.of("tipo_comprobante", "03"));
        emit(boleta, null).then().statusCode(201).body("serie_numero", equalTo("B001-1"));
        Map<String, Object> note = Map.of("document", Map.of("kind", "CreditNote", "spec", Map.of(
                "comprobante_afectado_serie_numero", "B001-1", "descripcion_sustento", "Devolución",
                "cliente", Map.of("tipo_documento_identidad", "1", "numero_documento_identidad", "12345678", "nombre", "Ana"),
                "detalles", List.of(Map.of("descripcion", "Item", "cantidad", 1, "precio", 100)))));
        Response emitted = emit(note, null);
        emitted.then().statusCode(201).body("serie_numero", equalTo("BC01-1")).body("tipo_comprobante", equalTo("07"));
        assertNotNull(emitted.path("affected_document_id"), "the note points at the boleta it affects");
    }

    @Test
    void idempotent_retries_return_the_same_document() {
        String key = UUID.randomUUID().toString();
        String first = emit(invoice(Map.of()), key).then().statusCode(201).extract().path("id");
        String again = emit(invoice(Map.of()), key).then().statusCode(201).extract().path("id");
        assertEquals(first, again);
        // Same key, different request: refused
        emit(invoice(Map.of("observaciones", "otra")), key).then().statusCode(422).body("kind", equalTo("idempotency_key_reused"));
    }

    @Test
    void concurrent_idempotent_retries_create_one_document() throws Exception {
        String key = UUID.randomUUID().toString();
        String label = UUID.randomUUID().toString();
        Map<String, Object> body = new HashMap<>(invoice(Map.of()));
        body.put("labels", Map.of("erp_id", label));
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            calls.add(() -> emit(body, key).statusCode());
        }
        List<Integer> statuses = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (Future<Integer> f : pool.invokeAll(calls)) {
                statuses.add(f.get());
            }
        }
        assertTrue(statuses.contains(201), statuses.toString());
        assertTrue(statuses.stream().allMatch(s -> s == 201 || s == 409), statuses.toString());
        as("alice").get(base + "/documents?label=erp_id:" + label).then().statusCode(200).body("items", hasSize(1));
    }

    @Test
    void concurrent_emits_on_one_series_get_consecutive_unique_numbers() throws Exception {
        int n = 12;
        List<Callable<String>> calls = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            calls.add(() -> emit(invoice(Map.of()), null).then().statusCode(201).extract().path("serie_numero"));
        }
        Set<String> numbers = new HashSet<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(n)) {
            for (Future<String> f : pool.invokeAll(calls)) {
                numbers.add(f.get());
            }
        }
        Set<String> expected = new HashSet<>();
        for (int i = 1; i <= n; i++) {
            expected.add("F001-" + i);
        }
        assertEquals(expected, numbers, "no duplicate and no skipped number");
    }

    @Test
    void an_invalid_document_consumes_no_number() {
        EngineStub.on("/v1/validate", body -> Reply.ok(
                "{\"valid\":false,\"errors\":[{\"code\":\"2329\",\"message\":\"fecha futura\"}],\"observations\":[]}"));
        emit(invoice(Map.of()), null).then().statusCode(422)
                .body("kind", equalTo("invalid_document"))
                .body("detail", org.hamcrest.Matchers.containsString("2329"));
        EngineStub.reset();
        emit(invoice(Map.of()), null).then().statusCode(201).body("serie_numero", equalTo("F001-1"));
    }

    @Test
    void the_issuer_ruc_must_be_the_companys() {
        emit(invoice(Map.of("proveedor", Map.of("ruc", "20999999999", "razon_social", "Otra"))), null)
                .then().statusCode(422).body("kind", equalTo("invalid_document"));
    }

    @Test
    void buyers_open_the_document_with_its_link_and_downloading_delivers_it() {
        Map<String, Object> body = new HashMap<>(invoice(Map.of()));
        body.put("delivered", false);
        Response emitted = emit(body, null);
        String id = emitted.path("id");
        String path = URI.create(emitted.<String>path("access_url")).getPath();
        await().atMost(WAIT).until(() -> "accepted".equals(status(id)));

        given().get(path).then().statusCode(200)
                .body("serie_numero", equalTo("F001-1"))
                .body("status", equalTo("aceptado"))
                .body("files", hasItem("xml"));
        as("alice").get(base + "/documents/" + id).then().body("delivered_at", org.hamcrest.Matchers.nullValue());
        given().get(path + "/xml").then().statusCode(200).contentType("application/xml");
        as("alice").get(base + "/documents/" + id).then().body("delivered_source", equalTo("service"));
        // A wrong or random token opens nothing
        given().get("/api/v1/public/documents/AAAAAAAAAAAAAAAAAAAAAA").then().statusCode(404);
    }

    @Test
    void documents_are_filtered_by_label_and_status() {
        String erp = UUID.randomUUID().toString();
        Map<String, Object> body = new HashMap<>(invoice(Map.of()));
        body.put("labels", Map.of("erp_id", erp));
        String id = emit(body, null).path("id");
        as("alice").get(base + "/documents?label=erp_id:" + erp).then().statusCode(200)
                .body("items", hasSize(1)).body("items[0].id", equalTo(id));
        as("alice").get(base + "/documents?company_id=" + company + "&tipo_comprobante=01").then().statusCode(200)
                .body("items.id", hasItem(id));
    }

    @Test
    void other_tenants_cannot_emit_or_read() {
        String id = emit(invoice(Map.of()), null).path("id");
        tenantOf("bob");
        as("bob").body(invoice(Map.of())).post(base + "/companies/" + company + "/documents").then().statusCode(404);
        as("bob").get(base + "/documents/" + id).then().statusCode(404);
    }

    @Test
    void production_needs_credentials_at_emit() {
        UUID production = companies.verified(tenant, "production");
        as("alice").body(invoice(Map.of())).post(base + "/companies/" + production + "/documents")
                .then().statusCode(422).body("kind", equalTo("no_credentials"));
    }

    @Test
    void a_rejected_document_that_was_delivered_raises_a_warning_webhook() throws Exception {
        try (WebhookReceiver receiver = new WebhookReceiver()) {
            String secret = as("alice").body(Map.of("url", receiver.url(), "events", List.of("document.*")))
                    .post(base + "/webhooks").then().statusCode(201).body("secret", startsWith("whsec_")).extract().path("secret");
            EngineStub.on("/v1/send", body -> body.contains("F001-1")
                    ? Reply.ok(sendAnswer("rejected", 2335, null, null)) : Reply.ok(sendAnswer("accepted", 0, null, null)));
            String id = emit(invoice(Map.of()), null).path("id");
            await().atMost(WAIT).until(() -> receiver.types().contains("document.rejected_after_delivery"));
            WebhookReceiver.Received rejected = receiver.received().stream()
                    .filter(r -> r.body().contains("\"document.rejected\"")).findFirst().orElseThrow();
            assertTrue(rejected.body().contains(id));
            assertTrue(receiver.verifies(rejected, secret), "HMAC signature verifies with the subscription's secret");
            assertEquals("application/cloudevents+json", rejected.contentType());
        }
    }
}
