package io.github.projectopenubl.xhandler.documents;

import static io.github.projectopenubl.xhandler.Api.as;
import static io.github.projectopenubl.xhandler.Api.tenantOf;
import static io.github.projectopenubl.xhandler.engine.EngineStub.Reply;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.projectopenubl.xhandler.company.TestCompanies;
import io.github.projectopenubl.xhandler.engine.EngineStub;
import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Manual issuing (DESIGN.md §9.1, §11.10): drafts without a number, edited, then issued through emit. */
@QuarkusTest
@QuarkusTestResource(EngineStub.class)
class DraftTest {

    @Inject
    TestCompanies companies;

    @Inject
    EntityManager em;

    @Inject
    TenantAccess access;

    @Inject
    DraftPurge purge;

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
        Instant start = QuarkusTransaction.requiringNew().call(() -> (Instant) em.createNativeQuery("SELECT clock_timestamp()").getSingleResult());
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
        assertEquals(List.of("F001-1"), numbers, "one document: the losing issues created none");
        String documentId = as("alice").get(base + "/drafts/" + id).then().extract().path("document_id");

        // The losers stopped before numbering and signing: one signature, the issued document's
        assertEquals(1, count("SELECT count(*) FROM xhandler.signature_log WHERE document_id = :d", Map.of("d", UUID.fromString(documentId))));
        assertEquals(0, count("""
                SELECT count(*) FROM xhandler.signature_log s
                WHERE s.at >= :start AND NOT EXISTS (SELECT 1 FROM xhandler.document d WHERE d.id = s.document_id)""", Map.of("start", start)),
                "a losing issue signed a document it never created");

        // and reserved no number: the next draft gets the next one
        String next = create(draft("Invoice", Map.of())).path("id");
        as("alice").post(base + "/drafts/" + next + "/issue").then().statusCode(201).body("serie_numero", equalTo("F001-2"));
    }

    @Test
    void a_retried_issue_with_its_idempotency_key_gets_the_same_document() {
        String id = create(draft("Invoice", Map.of())).path("id");
        String key = UUID.randomUUID().toString();
        String first = as("alice").header("Idempotency-Key", key).post(base + "/drafts/" + id + "/issue?version=1")
                .then().statusCode(201).body("serie_numero", equalTo("F001-1")).extract().path("id");
        // The draft is issued now, and its version moved on: the retry still replays the 201
        String again = as("alice").header("Idempotency-Key", key).post(base + "/drafts/" + id + "/issue?version=1")
                .then().statusCode(201).extract().path("id");
        assertEquals(first, again);
        // Another key is another issue: refused
        as("alice").header("Idempotency-Key", UUID.randomUUID().toString()).post(base + "/drafts/" + id + "/issue")
                .then().statusCode(409).body("kind", equalTo("draft_issued"));
        // The same key on another draft with the same content is not a replay
        String other = create(draft("Invoice", Map.of())).path("id");
        as("alice").header("Idempotency-Key", key).post(base + "/drafts/" + other + "/issue").then().statusCode(422)
                .body("kind", equalTo("idempotency_key_reused"));
        List<String> numbers = as("alice").get(base + "/documents?company_id=" + company).then().extract().path("items.serie_numero");
        assertEquals(List.of("F001-1"), numbers);
    }

    @Test
    void an_invoice_draft_takes_its_type_from_the_series() {
        String boleta = create(Map.of("kind", "Invoice", "spec", draft("Invoice", Map.of()).get("spec"), "serie", "B001"))
                .then().statusCode(201).body("tipo_comprobante", equalTo("03")).extract().path("id");
        String factura = create(Map.of("kind", "Invoice", "spec", draft("Invoice", Map.of()).get("spec"), "serie", "F001"))
                .then().statusCode(201).body("tipo_comprobante", equalTo("01")).extract().path("id");
        // An explicit type wins; editing the series changes the inferred one
        create(Map.of("kind", "Invoice", "spec", draft("Invoice", Map.of("tipo_comprobante", "01")).get("spec"), "serie", "F002"))
                .then().statusCode(201).body("tipo_comprobante", equalTo("01"));
        as("alice").body(Map.of("kind", "Invoice", "spec", draft("Invoice", Map.of()).get("spec"), "serie", "B001")).put(base + "/drafts/" + factura)
                .then().statusCode(200).body("tipo_comprobante", equalTo("03"));
        as("alice").body(Map.of("kind", "Invoice", "spec", draft("Invoice", Map.of()).get("spec"), "serie", "F001")).put(base + "/drafts/" + factura)
                .then().statusCode(200).body("tipo_comprobante", equalTo("01"));
        // Issued as a boleta in its series
        as("alice").post(base + "/drafts/" + boleta + "/issue").then().statusCode(201)
                .body("serie_numero", equalTo("B001-1")).body("tipo_comprobante", equalTo("03"));
    }

    @Test
    void viewers_cant_create_edit_or_issue_drafts() {
        UUID other = newTenant();
        UUID otherCompany = companies.verified(other, "fake");
        String otherBase = "/api/v1/tenants/" + other;
        String id = as("alice").body(draft("Invoice", Map.of())).post(otherBase + "/companies/" + otherCompany + "/drafts")
                .then().statusCode(201).extract().path("id");
        as("alice").body(Map.of("kind", "user", "subject", subject("dave"), "role", "viewer")).post(otherBase + "/members")
                .then().statusCode(201);

        as("dave").get(otherBase + "/drafts/" + id).then().statusCode(200);
        as("dave").body(draft("Invoice", Map.of())).post(otherBase + "/companies/" + otherCompany + "/drafts")
                .then().statusCode(403).body("kind", equalTo("forbidden"));
        as("dave").header("Idempotency-Key", UUID.randomUUID().toString()).body(draft("Invoice", Map.of()))
                .post(otherBase + "/companies/" + otherCompany + "/drafts").then().statusCode(403);
        as("dave").body(draft("Invoice", Map.of())).put(otherBase + "/drafts/" + id).then().statusCode(403);
        as("dave").post(otherBase + "/drafts/" + id + "/issue").then().statusCode(403);
        as("dave").delete(otherBase + "/drafts/" + id).then().statusCode(403);
        as("alice").get(otherBase + "/drafts/" + id).then().body("status", equalTo("draft")).body("version", equalTo(1));
    }

    @Test
    void drafts_hold_personal_data_until_purged_after_deletion() {
        String dni = String.valueOf(10_000_000 + ThreadLocalRandom.current().nextInt(89_999_999));
        Map<String, Object> spec = new HashMap<>(Map.of(
                "moneda", "PEN",
                "cliente", Map.of("tipo_documento_identidad", "1", "numero_documento_identidad", dni, "nombre", "PERSONA NATURAL"),
                "detalles", List.of()));
        String kept = create(Map.of("kind", "Invoice", "spec", spec)).path("id");
        String deleted = create(Map.of("kind", "Invoice", "spec", spec)).path("id");
        String expired = create(Map.of("kind", "Invoice", "spec", spec)).path("id");
        as("alice").delete(base + "/drafts/" + deleted).then().statusCode(204);
        as("alice").delete(base + "/drafts/" + expired).then().statusCode(204);

        // The person's data includes the drafts naming them, deleted ones too
        String request = as("alice").body(Map.of("subject_doc_type", "1", "subject_doc_number", dni, "kind", "access"))
                .post(base + "/data-subject-requests").then().statusCode(200).extract().path("id");
        as("alice").get(base + "/data-subject-requests/" + request + "/personal-data").then().statusCode(200)
                .body("drafts.id", containsInAnyOrder(kept, deleted, expired))
                .body("drafts.find { it.id == '" + deleted + "' }.deleted_at", notNullValue())
                .body("drafts.find { it.id == '" + kept + "' }.nombre", equalTo("PERSONA NATURAL"));

        // 30 days after deletion a draft is purged for good; younger deletions and live drafts stay
        QuarkusTransaction.requiringNew().run(() -> {
            access.enterAsPlatform(tenant);
            em.createNativeQuery("UPDATE xhandler.document_draft SET deleted_at = now() - interval '31 days' WHERE id = :id")
                    .setParameter("id", UUID.fromString(expired)).executeUpdate();
        });
        Map<String, Object> result = purge.run(new JobContext(UUID.randomUUID(), null, "drafts.purge", 1,
                new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode(), null, () -> false));
        assertTrue(((Number) result.get("drafts")).intValue() >= 1, result.toString());
        assertEquals(0, count("SELECT count(*) FROM xhandler.document_draft WHERE id = :id", Map.of("id", UUID.fromString(expired))));
        assertEquals(2, count("SELECT count(*) FROM xhandler.document_draft WHERE id IN (:a, :b)",
                Map.of("a", UUID.fromString(kept), "b", UUID.fromString(deleted))));
        as("alice").get(base + "/data-subject-requests/" + request + "/personal-data").then()
                .body("drafts.id", containsInAnyOrder(kept, deleted));
    }

    long count(String sql, Map<String, Object> params) {
        return QuarkusTransaction.requiringNew().call(() -> {
            access.enterAsPlatform(tenant);
            var query = em.createNativeQuery(sql);
            params.forEach(query::setParameter);
            return ((Number) query.getSingleResult()).longValue();
        });
    }

    /** A second tenant owned by alice (users have one each; more come through a partner). */
    static UUID newTenant() {
        String partner = as("olga").body(Map.of("name", "Partner " + UUID.randomUUID())).post("/api/admin/v1/partners")
                .then().statusCode(200).extract().path("id");
        String sub = subject("alice");
        as("olga").body(Map.of("kind", "user", "subject", sub, "role", "admin")).put("/api/admin/v1/partners/" + partner + "/members")
                .then().statusCode(200);
        return UUID.fromString(as("alice").body(Map.of("name", "Borradores " + UUID.randomUUID(), "owner_kind", "user", "owner_subject", sub))
                .post("/api/v1/partners/" + partner + "/tenants").then().statusCode(201).extract().path("id"));
    }

    static String subject(String user) {
        String token = new io.quarkus.test.keycloak.client.KeycloakTestClient().getAccessToken(user);
        String payload = new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]), java.nio.charset.StandardCharsets.UTF_8);
        int i = payload.indexOf("\"sub\":\"") + 7;
        return payload.substring(i, payload.indexOf('"', i));
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
