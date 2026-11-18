package io.github.projectopenubl.xhandler.documents;

import static io.github.projectopenubl.xhandler.Api.as;
import static io.github.projectopenubl.xhandler.Api.tenantOf;
import static io.github.projectopenubl.xhandler.engine.EngineStub.Reply;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.projectopenubl.xhandler.company.TestCompanies;
import io.github.projectopenubl.xhandler.engine.EngineStub;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Manual issuing (DESIGN.md §9.1, §11.10): drafts without a number, edited, then issued through emit. */
@QuarkusTest
@QuarkusTestResource(EngineStub.class)
class DraftTest {

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

    static Map<String, Object> draft(String kind, Map<String, Object> extra) {
        Map<String, Object> spec = new HashMap<>(Map.of(
                "moneda", "PEN",
                "cliente", Map.of("tipo_documento_identidad", "6", "numero_documento_identidad", "12121212121", "nombre", "Cliente SAC"),
                "detalles", List.of(Map.of("descripcion", "Item", "cantidad", 2, "precio", 50))));
        spec.putAll(extra);
        return Map.of("kind", kind, "spec", spec);
    }

    Response create(Object body) {
        return as("alice").body(body).post(base + "/companies/" + company + "/drafts");
    }

    @Test
    void a_draft_is_edited_without_a_number_then_issued_once() {
        Response created = create(draft("Invoice", Map.of()));
        created.then().statusCode(201)
                .body("status", equalTo("draft"))
                .body("version", equalTo(1))
                .body("tipo_comprobante", equalTo("01"))
                .body("customer.nombre", equalTo("Cliente SAC"))
                .body("moneda", equalTo("PEN"))
                .body("prn", org.hamcrest.Matchers.startsWith("xh:draft:"))
                .body("document_id", nullValue());
        String id = created.path("id");

        // Edit: the version moves on; an edit of an older version is refused
        Map<String, Object> edited = new HashMap<>(draft("Invoice", Map.of("observaciones", "Entrega en tienda")));
        edited.put("version", 1);
        as("alice").body(edited).put(base + "/drafts/" + id).then().statusCode(200)
                .body("version", equalTo(2))
                .body("spec.observaciones", equalTo("Entrega en tienda"));
        as("alice").body(edited).put(base + "/drafts/" + id).then().statusCode(409).body("kind", equalTo("draft_changed"));

        as("alice").get(base + "/drafts?status=draft&company_id=" + company).then().statusCode(200).body("items.id", hasItem(id));

        // Issue: only the version the user reviewed, numbered by emit like any document
        as("alice").post(base + "/drafts/" + id + "/issue?version=1").then().statusCode(409).body("kind", equalTo("draft_changed"));
        Response issued = as("alice").post(base + "/drafts/" + id + "/issue?version=2");
        issued.then().statusCode(201)
                .body("serie_numero", equalTo("F001-1"))
                .body("status", equalTo("pending_send"))
                .body("access_url", notNullValue());
        String documentId = issued.path("id");
        as("alice").get(base + "/drafts/" + id).then().statusCode(200)
                .body("status", equalTo("issued"))
                .body("document_id", equalTo(documentId));
        as("alice").get(base + "/drafts?status=draft&company_id=" + company).then().body("items.id", not(hasItem(id)));
        as("alice").get(base + "/drafts?status=issued&company_id=" + company).then().body("items.id", hasItem(id));

        // An issued draft is history: no edit, no second issue, no delete
        as("alice").body(draft("Invoice", Map.of())).put(base + "/drafts/" + id).then().statusCode(409).body("kind", equalTo("draft_issued"));
        as("alice").post(base + "/drafts/" + id + "/issue").then().statusCode(409).body("kind", equalTo("draft_issued"));
        as("alice").delete(base + "/drafts/" + id).then().statusCode(409);
    }

    @Test
    void boletas_and_notes_are_drafted_and_issued_in_their_series() {
        String boleta = create(draft("Invoice", Map.of("tipo_comprobante", "03"))).then().statusCode(201)
                .body("tipo_comprobante", equalTo("03")).extract().path("id");
        as("alice").post(base + "/drafts/" + boleta + "/issue").then().statusCode(201).body("serie_numero", equalTo("B001-1"));

        String note = create(draft("CreditNote", Map.of("comprobante_afectado_serie_numero", "B001-1", "descripcion_sustento", "Devolución")))
                .then().statusCode(201).body("tipo_comprobante", equalTo("07")).extract().path("id");
        as("alice").post(base + "/drafts/" + note + "/issue").then().statusCode(201)
                .body("serie_numero", equalTo("BC01-1"))
                .body("affected_document_id", notNullValue());
    }

    @Test
    void an_invalid_document_keeps_the_draft_editable_and_consumes_no_number() {
        String id = create(draft("Invoice", Map.of())).path("id");
        EngineStub.on("/v1/validate", body -> Reply.ok(
                "{\"valid\":false,\"errors\":[{\"code\":\"3105\",\"message\":\"falta algo\"}],\"observations\":[]}"));
        as("alice").post(base + "/drafts/" + id + "/issue").then().statusCode(422).body("kind", equalTo("invalid_document"));
        as("alice").get(base + "/drafts/" + id).then().body("status", equalTo("draft")).body("version", equalTo(1));
        EngineStub.reset();
        as("alice").post(base + "/drafts/" + id + "/issue").then().statusCode(201).body("serie_numero", equalTo("F001-1"));
    }

    @Test
    void concurrent_issues_of_one_draft_give_one_document() throws Exception {
        String id = create(draft("Invoice", Map.of())).path("id");
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            calls.add(() -> as("alice").post(base + "/drafts/" + id + "/issue").statusCode());
        }
        List<Integer> statuses = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(6)) {
            for (Future<Integer> f : pool.invokeAll(calls)) {
                statuses.add(f.get());
            }
        }
        assertEquals(1, statuses.stream().filter(s -> s == 201).count(), statuses.toString());
        assertTrue(statuses.stream().allMatch(s -> s == 201 || s == 409 || s == 503), statuses.toString());
        List<String> numbers = as("alice").get(base + "/documents?company_id=" + company).then().statusCode(200)
                .extract().path("items.serie_numero");
        assertEquals(List.of("F001-1"), numbers, "one document, and the number after it is unused");
    }

    @Test
    void drafts_carry_no_number_and_no_summaries() {
        create(draft("Invoice", Map.of("serie_numero", "F001-7"))).then().statusCode(422).body("kind", equalTo("invalid_request"));
        create(draft("VoidedDocuments", Map.of())).then().statusCode(422);
        create(Map.of("kind", "Invoice")).then().statusCode(422);
        create(Map.of("kind", "Invoice", "spec", Map.of(), "serie", "f1")).then().statusCode(422);
    }

    @Test
    void a_deleted_draft_is_gone() {
        String id = create(draft("Invoice", Map.of())).path("id");
        as("alice").delete(base + "/drafts/" + id).then().statusCode(204);
        as("alice").get(base + "/drafts/" + id).then().statusCode(404);
        as("alice").post(base + "/drafts/" + id + "/issue").then().statusCode(404);
        as("alice").get(base + "/drafts?company_id=" + company).then().body("items.id", not(hasItem(id)));
    }

    @Test
    void idempotent_creates_return_the_same_draft() {
        String key = UUID.randomUUID().toString();
        String first = as("alice").header("Idempotency-Key", key).body(draft("Invoice", Map.of())).post(base + "/companies/" + company + "/drafts")
                .then().statusCode(201).extract().path("id");
        String again = as("alice").header("Idempotency-Key", key).body(draft("Invoice", Map.of())).post(base + "/companies/" + company + "/drafts")
                .then().statusCode(201).extract().path("id");
        assertEquals(first, again);
    }

    @Test
    void another_tenants_drafts_are_not_found() {
        String id = create(draft("Invoice", Map.of())).path("id");
        UUID bobs = tenantOf("bob");
        as("bob").get("/api/v1/tenants/" + tenant + "/drafts/" + id).then().statusCode(404);
        as("bob").get("/api/v1/tenants/" + bobs + "/drafts/" + id).then().statusCode(404);
        as("bob").post("/api/v1/tenants/" + bobs + "/drafts/" + id + "/issue").then().statusCode(404);
        as("bob").body(draft("Invoice", Map.of())).post("/api/v1/tenants/" + bobs + "/companies/" + company + "/drafts").then().statusCode(404);
    }

    @Test
    void the_buyer_portal_links_the_issuers_libro_de_reclamaciones() {
        as("alice").body(Map.of("libro_reclamaciones_url", "http://inseguro.example")).put(base + "/companies/" + company + "/settings")
                .then().statusCode(422);
        as("alice").body(Map.of("libro_reclamaciones_url", "https://empresa.example/reclamaciones")).put(base + "/companies/" + company + "/settings")
                .then().statusCode(200).body("libro_reclamaciones_url", equalTo("https://empresa.example/reclamaciones"));

        String id = create(draft("Invoice", Map.of())).path("id");
        String link = URI.create(as("alice").post(base + "/drafts/" + id + "/issue").then().statusCode(201).extract().<String>path("access_url")).getPath();
        given().get("/api/v1/public/documents/" + link.substring(link.lastIndexOf('/') + 1)).then().statusCode(200)
                .body("libro_reclamaciones_url", equalTo("https://empresa.example/reclamaciones"));

        // Blank removes it
        as("alice").body(Map.of("libro_reclamaciones_url", " ")).put(base + "/companies/" + company + "/settings")
                .then().statusCode(200).body("libro_reclamaciones_url", nullValue());
    }
}
