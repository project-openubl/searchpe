package io.github.projectopenubl.xhandler.billing;

import static io.github.projectopenubl.xhandler.Api.as;
import static io.github.projectopenubl.xhandler.Api.tenantOf;
import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.projectopenubl.xhandler.company.TestCompanies;
import io.github.projectopenubl.xhandler.documents.TestDocuments;
import io.github.projectopenubl.xhandler.engine.EngineStub;
import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** P6: partners, quotas, Lago usage and entitlements, self-invoicing. */
@QuarkusTest
@QuarkusTestResource(EngineStub.class)
class BillingTest {

    @Inject
    TestCompanies companies;

    @Inject
    TestDocuments docs;

    @Inject
    BillingJobs.UsageDelivery usageDelivery;

    @Inject
    BillingJobs.EntitlementSync entitlementSync;

    @Inject
    TenantAccess access;

    @AfterEach
    void tearDown() {
        EngineStub.reset();
    }

    static JobContext context(String name) {
        return new JobContext(UUID.randomUUID(), null, name, 1, com.fasterxml.jackson.databind.node.NullNode.getInstance(), null, () -> false);
    }

    static String subject(String user) {
        String token = new io.quarkus.test.keycloak.client.KeycloakTestClient().getAccessToken(user);
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        int i = payload.indexOf("\"sub\":\"") + 7;
        return payload.substring(i, payload.indexOf('"', i));
    }

    /** A new partner with alice as its admin, and a fresh tenant created through the partner API. */
    UUID[] partnerTenant() {
        String partner = as("olga").body(Map.of("name", "Partner " + UUID.randomUUID())).post("/api/admin/v1/partners")
                .then().statusCode(200).extract().path("id");
        as("olga").body(Map.of("kind", "user", "subject", subject("alice"), "role", "admin"))
                .put("/api/admin/v1/partners/" + partner + "/members").then().statusCode(200);
        String tenant = as("alice").body(Map.of("name", "Cliente del partner")).post("/api/v1/partners/" + partner + "/tenants")
                .then().statusCode(201).extract().path("id");
        return new UUID[] {UUID.fromString(partner), UUID.fromString(tenant)};
    }

    static Map<String, Object> invoice() {
        return Map.of("document", Map.of("kind", "Invoice", "spec", Map.of(
                "cliente", Map.of("tipo_documento_identidad", "6", "numero_documento_identidad", "20111111111", "nombre", "X"),
                "detalles", List.of())));
    }

    @Test
    void partners_create_and_manage_their_tenants_and_see_their_usage() {
        UUID[] ids = partnerTenant();
        UUID partner = ids[0];
        UUID tenant = ids[1];
        as("alice").get("/api/v1/partners").then().statusCode(200).body("id", hasItem(partner.toString()));
        // Partner admins administer their tenants
        as("alice").body(Map.of("ruc", io.github.projectopenubl.xhandler.Api.randomRuc(), "razon_social", "Empresa del cliente"))
                .post("/api/v1/tenants/" + tenant + "/companies").then().statusCode(201);
        as("alice").get("/api/v1/partners/" + partner + "/tenants").then().statusCode(200)
                .body("find { it.id == '" + tenant + "' }.plan", equalTo("free"));
        as("alice").body(Map.of("color", "#aa0000", "footer", "Facturación por Partner")).put("/api/v1/partners/" + partner + "/branding")
                .then().statusCode(200);
        // Other people see nothing
        as("bob").get("/api/v1/partners/" + partner + "/tenants").then().statusCode(404);
        as("bob").get("/api/v1/tenants/" + tenant + "/companies").then().statusCode(404);
    }

    UUID productionCompany(UUID tenant) throws Exception {
        String ruc = io.github.projectopenubl.xhandler.Api.randomRuc();
        UUID production = companies.verified(tenant, "production", ruc);
        java.io.File pfx = io.github.projectopenubl.xhandler.keys.TestCertificates.generate(ruc);
        given().auth().oauth2(new io.quarkus.test.keycloak.client.KeycloakTestClient().getAccessToken("alice"))
                .multiPart("file", pfx, "application/x-pkcs12").multiPart("password", io.github.projectopenubl.xhandler.keys.TestCertificates.PASSWORD)
                .put("/api/v1/tenants/" + tenant + "/companies/" + production + "/certificate").then().statusCode(200);
        as("alice").body(Map.of("username", ruc + "USER", "password", "x"))
                .put("/api/v1/tenants/" + tenant + "/companies/" + production + "/credentials/sol").then().statusCode(200);
        return production;
    }

    @Test
    void the_monthly_production_quota_is_enforced_but_replays_still_answer() throws Exception {
        UUID tenant = partnerTenant()[1];
        UUID company = productionCompany(tenant);
        as("alice").get("/api/v1/tenants/" + tenant + "/billing").then().statusCode(200)
                .body("usage.plan.code", equalTo("free")).body("usage.plan.production_per_month", equalTo(100));
        for (int i = 0; i < 99; i++) {
            docs.pending(tenant, company, "t-" + UUID.randomUUID() + ":invoice", d -> {
                d.sunatEnv = "production";
                d.status = "accepted";
                d.nextAttemptAt = null;
            });
        }
        String path = "/api/v1/tenants/" + tenant + "/companies/" + company + "/documents";
        String key = UUID.randomUUID().toString();
        String hundredth = as("alice").header("Idempotency-Key", key).body(invoice()).post(path).then().statusCode(201).extract().path("id");
        as("alice").body(invoice()).post(path).then().statusCode(402).body("kind", equalTo("quota_exceeded"));
        // A lost response retried with the same key gets the stored answer, not the quota error
        as("alice").header("Idempotency-Key", key).body(invoice()).post(path).then().statusCode(201).body("id", equalTo(hundredth));
        as("alice").get("/api/v1/tenants/" + tenant + "/notifications").then().body("items.kind", hasItem("quota"));
        // A bigger plan lifts it
        as("olga").body(Map.of("plan", "enterprse")).put("/api/admin/v1/tenants/" + tenant + "/plan").then().statusCode(422);
        as("olga").body(Map.of("plan", "enterprise")).put("/api/admin/v1/tenants/" + tenant + "/plan").then().statusCode(200);
        as("alice").get("/api/v1/tenants/" + tenant + "/billing").then().body("usage.plan.code", equalTo("enterprise"));
    }

    @Test
    void usage_goes_to_lago_and_plans_come_back() throws Exception {
        UUID tenant = partnerTenant()[1];
        UUID company = companies.verified(tenant, "fake");
        String id = as("alice").body(invoice()).post("/api/v1/tenants/" + tenant + "/companies/" + company + "/documents")
                .then().statusCode(201).extract().path("id");
        await().atMost(Duration.ofSeconds(20)).until(() -> "accepted".equals(
                as("alice").get("/api/v1/tenants/" + tenant + "/documents/" + id).then().extract().path("status")));
        usageDelivery.run(context("billing.usage-delivery"));
        assertTrue(EngineStub.requests().stream().anyMatch(r -> r.path().endsWith("/events/batch") && r.body().contains(tenant.toString())
                && r.body().contains("document_accepted") && r.body().contains("Bearer lago-key")), "usage reached Lago");

        EngineStub.LAGO_PLAN.set("business");
        QuarkusTransaction.requiringNew().run(() -> {
            access.enterAsPlatform(tenant);
            io.github.projectopenubl.xhandler.documents.Document.getEntityManager().createNativeQuery(
                    "UPDATE xhandler.entitlement SET source = 'lago', synced_at = now() - interval '30 days' WHERE tenant_id = :t")
                    .setParameter("t", tenant).executeUpdate();
        });
        entitlementSync.run(context("billing.entitlement-sync"));
        as("alice").get("/api/v1/tenants/" + tenant + "/billing").then().body("usage.plan.code", equalTo("business"))
                .body("usage.source", equalTo("lago"));
    }

    static String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("lago-test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void lago_subscription_webhooks_set_the_plan() throws Exception {
        UUID tenant = partnerTenant()[1];
        String event = "{\"webhook_type\":\"subscription.started\",\"subscription\":{\"external_customer_id\":\"" + tenant
                + "\",\"plan_code\":\"starter\",\"status\":\"active\"}}";
        given().header("X-Lago-Signature", sign(event)).body(event).post("/api/v1/public/lago/webhooks").then().statusCode(204);
        as("alice").get("/api/v1/tenants/" + tenant + "/billing").then().body("usage.plan.code", equalTo("starter"));
        String bad = "{\"webhook_type\":\"invoice.created\",\"invoice\":{\"lago_id\":\"x\",\"customer\":{\"external_id\":\"not-a-uuid\"}}}";
        given().header("X-Lago-Signature", sign(bad)).body(bad).post("/api/v1/public/lago/webhooks").then().statusCode(422);
    }

    @Test
    void lago_invoices_become_real_comprobantes() throws Exception {
        UUID issuerTenant = tenantOf("alice");
        UUID issuerCompany = companies.verified(issuerTenant, "fake");
        as("olga").body(Map.of("tenant_id", issuerTenant.toString(), "company_id", issuerCompany.toString()))
                .put("/api/admin/v1/billing/issuer").then().statusCode(200);
        UUID customer = partnerTenant()[1];
        as("alice").body(Map.of("billing_ruc", "20999999999", "billing_name", "Cliente Facturable SAC"))
                .put("/api/v1/tenants/" + customer + "/billing").then().statusCode(200);
        String lagoId = UUID.randomUUID().toString();
        String event = "{\"webhook_type\":\"invoice.created\",\"invoice\":{\"lago_id\":\"" + lagoId + "\",\"number\":\"LAGO-1\","
                + "\"currency\":\"PEN\",\"customer\":{\"external_id\":\"" + customer + "\"},\"total_amount_cents\":11682,"
                + "\"fees\":[{\"amount_cents\":9900,\"item\":{\"name\":\"Plan Emprendedor\"}}]}}";
        given().header("X-Lago-Signature", "bad").body(event).post("/api/v1/public/lago/webhooks").then().statusCode(401);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("lago-test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = Base64.getEncoder().encodeToString(mac.doFinal(event.getBytes(StandardCharsets.UTF_8)));
        given().header("X-Lago-Signature", signature).body(event).post("/api/v1/public/lago/webhooks").then().statusCode(204);
        // The same webhook twice issues one comprobante
        given().header("X-Lago-Signature", signature).body(event).post("/api/v1/public/lago/webhooks").then().statusCode(204);
        String path = "/api/v1/tenants/" + issuerTenant + "/documents?label=lago_invoice:" + lagoId;
        await().atMost(Duration.ofSeconds(20)).until(() -> as("alice").get(path).then().extract().jsonPath().getList("items").size() == 1);
        as("alice").get(path).then().body("items[0].tipo_comprobante", equalTo("01"))
                .body("items[0].customer.nombre", equalTo("Cliente Facturable SAC"));
    }
}
