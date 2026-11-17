package io.github.projectopenubl.xhandler.privacy;

import static io.github.projectopenubl.xhandler.Api.as;
import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.projectopenubl.xhandler.company.TestCompanies;
import io.github.projectopenubl.xhandler.documents.TestDocuments;
import io.github.projectopenubl.xhandler.engine.EngineStub;
import io.github.projectopenubl.xhandler.exit.TenantExit;
import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.ops.RetentionPurge;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** P11: data-subject requests, export and exit, deletion with evidence, retention, health metrics. */
@QuarkusTest
@QuarkusTestResource(EngineStub.class)
class DataProtectionTest {

    @Inject
    TestCompanies companies;

    @Inject
    TestDocuments docs;

    @Inject
    TenantExit.Deletion deletion;

    @Inject
    RetentionPurge retention;

    @Inject
    EntityManager em;

    @Inject
    io.github.projectopenubl.xhandler.storage.FileStore files;

    @AfterEach
    void tearDown() {
        EngineStub.reset();
    }

    static JobContext context(String name) {
        return new JobContext(UUID.randomUUID(), null, name, 1, new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode(), null, () -> false);
    }

    /** Users have one tenant each; more come through a partner, with alice as owner. */
    UUID newTenant(String name) {
        String partner = as("olga").body(Map.of("name", "Partner " + UUID.randomUUID())).post("/api/admin/v1/partners")
                .then().statusCode(200).extract().path("id");
        String sub = subject("alice");
        as("olga").body(Map.of("kind", "user", "subject", sub, "role", "admin")).put("/api/admin/v1/partners/" + partner + "/members")
                .then().statusCode(200);
        return UUID.fromString(as("alice").body(Map.of("name", name, "owner_kind", "user", "owner_subject", sub))
                .post("/api/v1/partners/" + partner + "/tenants").then().statusCode(201).extract().path("id"));
    }

    static String subject(String user) {
        String token = new io.quarkus.test.keycloak.client.KeycloakTestClient().getAccessToken(user);
        String payload = new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]), java.nio.charset.StandardCharsets.UTF_8);
        int i = payload.indexOf("\"sub\":\"") + 7;
        return payload.substring(i, payload.indexOf('"', i));
    }

    void sql(String statement, Map<String, Object> params) {
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("SELECT set_config('app.tenant_id', :t, true)").setParameter("t", params.get("t").toString()).getSingleResult();
            var query = em.createNativeQuery(statement);
            params.forEach(query::setParameter);
            query.executeUpdate();
        });
    }

    @Test
    void data_subject_requests_reach_the_tenant_with_what_we_hold() {
        UUID tenant = newTenant("Privacidad " + UUID.randomUUID());
        UUID company = companies.verified(tenant, "fake");
        String dni = String.valueOf(10_000_000 + (int) (Math.random() * 89_999_999));
        docs.pending(tenant, company, "fake:bill", d -> {
            d.status = "accepted";
            d.customerTipo = "1";
            d.customerNumero = dni;
            d.customerNombre = "PERSONA NATURAL";
        });
        // A person writes to the platform: an admin registers it and it reaches the tenant at once
        String id = as("olga").body(Map.of("subject_doc_type", "1", "subject_doc_number", dni, "kind", "access"))
                .post("/api/admin/v1/data-protection/tenants/" + tenant + "/data-subject-requests").then().statusCode(200).extract().path("id").toString();
        as("alice").body(Map.of("subject_doc_type", "1", "subject_doc_number", dni, "kind", "access"))
                .post("/api/admin/v1/data-protection/tenants/" + tenant + "/data-subject-requests").then().statusCode(403);
        String base = "/api/v1/tenants/" + tenant + "/data-subject-requests";
        as("alice").get(base).then().statusCode(200).body("id", hasItem(id)).body("channel", hasItem("platform"));
        as("alice").get("/api/v1/tenants/" + tenant + "/notifications").then().body("items.kind", hasItem("privacy.request"));
        as("alice").get(base + "/" + id + "/personal-data").then().statusCode(200).body("documents.size()", equalTo(1))
                .body("documents[0].nombre", equalTo("PERSONA NATURAL"));
        as("bob").get(base).then().statusCode(404);
        as("alice").body(Map.of("resolution", "Se entregó la información.")).post(base + "/" + id + "/close").then().statusCode(200)
                .body("status", equalTo("closed"));
        as("alice").body(Map.of("resolution", "otra vez")).post(base + "/" + id + "/close").then().statusCode(404);
    }

    @Test
    void a_tenant_exports_leaves_and_is_deleted_with_evidence() throws Exception {
        String name = "Salida " + UUID.randomUUID();
        UUID tenant = newTenant(name);
        UUID company = companies.verified(tenant, "fake");
        UUID old = docs.pending(tenant, company, "fake:bill", d -> {
            d.status = "accepted";
            d.fechaEmision = LocalDate.of(2012, 3, 1);
        }).id();
        UUID current = docs.pending(tenant, company, "fake:bill", d -> d.status = "accepted").id();

        // Retention: with 10 years, the 2012 document goes and today's stays; an audit hold keeps everything
        as("olga").body(Map.of("audit_hold", true)).put("/api/admin/v1/data-protection/tenants/" + tenant + "/audit-hold").then().statusCode(200);
        retention.run(context("retention.purge"));
        assertEquals(2, count(tenant, "SELECT count(*) FROM xhandler.document WHERE tenant_id = :p", tenant));
        as("olga").body(Map.of("audit_hold", false)).put("/api/admin/v1/data-protection/tenants/" + tenant + "/audit-hold").then().statusCode(200);
        retention.run(context("retention.purge"));
        assertEquals(0, count(tenant, "SELECT count(*) FROM xhandler.document WHERE id = :p", old));
        assertEquals(1, count(tenant, "SELECT count(*) FROM xhandler.document WHERE id = :p", current));

        // Exit waits for documents still going to SUNAT
        String base = "/api/v1/tenants/" + tenant;
        UUID pending = docs.pending(tenant, company, "fake:bill", d -> d.holdReason = "operator").id();
        as("alice").body(Map.of("confirm_name", name)).post(base + "/exit").then().statusCode(409).body("kind", equalTo("pending_documents"));
        docs.update(tenant, pending, d -> d.status = "accepted");
        // and needs a fresh export (one an hour) and the name typed
        as("alice").body(Map.of("confirm_name", name)).post(base + "/exit").then().statusCode(409).body("kind", equalTo("export_required"));
        String export = as("alice").post(base + "/exports").then().statusCode(202).extract().path("id");
        as("alice").post(base + "/exports").then().statusCode(409).body("kind", equalTo("export_recent"));
        await().atMost(Duration.ofSeconds(30)).until(() -> "done".equals(
                as("alice").get(base + "/exports").then().extract().path("find { it.id == '" + export + "' }.status")));
        List<String> names = entries(as("alice").get(base + "/exports/" + export + "/parts/1").then().statusCode(200).extract().asByteArray());
        assertTrue(names.stream().anyMatch(n -> n.endsWith(".xml")) && names.stream().anyMatch(n -> n.endsWith(".json"))
                && names.stream().anyMatch(n -> n.endsWith(".pdf")), names.toString());
        // Named like SUNAT, with the issuer's RUC: two companies never collide
        String ruc = as("alice").get(base + "/companies/" + company).then().extract().path("ruc");
        assertTrue(names.stream().anyMatch(n -> n.startsWith("fake/" + ruc + "-01-")), names.toString());
        as("alice").body(Map.of("confirm_name", "otro")).post(base + "/exit").then().statusCode(422);
        as("alice").body(Map.of("confirm_name", name)).post(base + "/exit").then().statusCode(200).body("status", equalTo("exited"));
        // Exited: still readable (exports, data-subject requests) but no new documents
        as("alice").get(base + "/exports").then().statusCode(200);
        as("alice").body(Map.of("document", Map.of("kind", "Invoice", "spec", Map.of("cliente", Map.of("tipo_documento_identidad", "6",
                        "numero_documento_identidad", "20111111111", "nombre", "X"), "detalles", List.of()))))
                .post(base + "/companies/" + company + "/documents").then().statusCode(409).body("kind", equalTo("tenant_exited"));

        // After the exit period its data is deleted, with evidence
        sql("UPDATE xhandler.tenant SET delete_after = now() - interval '1 day' WHERE id = :t", Map.of("t", tenant));
        deletion.run(context("tenants.exit-deletion"));
        assertEquals(0, count(tenant, "SELECT count(*) FROM xhandler.document WHERE tenant_id = :p", tenant));
        assertEquals(0, count(tenant, "SELECT count(*) FROM xhandler.company WHERE tenant_id = :p", tenant));
        assertEquals(1, count(tenant, "SELECT count(*) FROM xhandler.tenant_deletion WHERE tenant_id = :p", tenant));
        assertEquals(1, count(tenant, "SELECT count(*) FROM xhandler.tenant WHERE id = :p AND deleted_at IS NOT NULL", tenant));
        assertTrue(files.list(io.github.projectopenubl.xhandler.storage.FileStore.keyOf(tenant, "")).isEmpty(), "every file of the tenant is gone");
        assertEquals("(eliminada)", QuarkusTransaction.requiringNew().call(() -> {
            em.createNativeQuery("SELECT set_config('app.tenant_id', :t, true)").setParameter("t", tenant.toString()).getSingleResult();
            return (String) em.createNativeQuery("SELECT name FROM xhandler.tenant WHERE id = :p").setParameter("p", tenant).getSingleResult();
        }));
        as("olga").post("/api/admin/v1/data-protection/tenants/" + tenant + "/exit/cancel").then().statusCode(404);
    }

    @Test
    void health_metrics_are_exposed() {
        String metrics = given().get("/q/metrics").then().statusCode(200).extract().asString();
        assertTrue(metrics.contains("xhandler_documents_exception") && metrics.contains("xhandler_jobs_oldest_waiting_seconds")
                && metrics.contains("xhandler_ose_forward_oldest_seconds"), "SLO gauges");
    }

    /** Counts with the tenant opened, as row-level security requires. */
    long count(UUID tenant, String sql, UUID param) {
        return QuarkusTransaction.requiringNew().call(() -> {
            em.createNativeQuery("SELECT set_config('app.tenant_id', :t, true)").setParameter("t", tenant.toString()).getSingleResult();
            return ((Number) em.createNativeQuery(sql).setParameter("p", param).getSingleResult()).longValue();
        });
    }

    static List<String> entries(byte[] zip) throws Exception {
        List<String> names = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                names.add(e.getName());
            }
        }
        return names;
    }
}
