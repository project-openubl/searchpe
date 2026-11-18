package io.github.projectopenubl.xhandler.api;

import static io.github.projectopenubl.xhandler.Api.as;
import static io.github.projectopenubl.xhandler.Api.randomRuc;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

import io.github.projectopenubl.xhandler.company.TestCompanies;
import io.github.projectopenubl.xhandler.documents.TestDocuments;
import io.github.projectopenubl.xhandler.engine.EngineStub;
import io.github.projectopenubl.xhandler.tenancy.TenantAccess;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Endpoints the console needed (DESIGN.md §12, §21.3): members, tenant settings, series, branding, SIRE, reads, filters. */
@QuarkusTest
@QuarkusTestResource(EngineStub.class)
class ConsoleApiTest {

    @Inject
    TestCompanies companies;

    @Inject
    TestDocuments docs;

    @Inject
    TenantAccess access;

    @Inject
    EntityManager em;

    @AfterEach
    void tearDown() {
        EngineStub.reset();
    }

    /** A new partner with alice as its admin. */
    static UUID newPartner() {
        String partner = as("olga").body(Map.of("name", "Partner " + UUID.randomUUID())).post("/api/admin/v1/partners")
                .then().statusCode(200).extract().path("id");
        as("olga").body(Map.of("kind", "user", "subject", subject("alice"), "role", "admin"))
                .put("/api/admin/v1/partners/" + partner + "/members").then().statusCode(200);
        return UUID.fromString(partner);
    }

    /** A fresh tenant owned by alice (users have one free tenant each; more come through a partner). */
    static UUID newTenant() {
        UUID partner = newPartner();
        return UUID.fromString(as("alice").body(Map.of("name", "Consola " + UUID.randomUUID(), "owner_kind", "user",
                        "owner_subject", subject("alice")))
                .post("/api/v1/partners/" + partner + "/tenants").then().statusCode(201).extract().path("id"));
    }

    static String subject(String user) {
        String token = new KeycloakTestClient().getAccessToken(user);
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        int i = payload.indexOf("\"sub\":\"") + 7;
        return payload.substring(i, payload.indexOf('"', i));
    }

    @Test
    void admins_change_and_remove_members_but_a_tenant_keeps_an_owner() {
        UUID tenant = newTenant();
        String base = "/api/v1/tenants/" + tenant + "/members";
        String alice = as("alice").get(base).then().statusCode(200).extract().path("find { it.role == 'owner' }.id");
        String dave = as("alice").body(Map.of("kind", "user", "subject", subject("dave"), "role", "admin")).post(base)
                .then().statusCode(201).extract().path("id");

        // Owners are changed by owners only, and the last one stays
        as("dave").body(Map.of("role", "viewer")).put(base + "/" + alice).then().statusCode(403).body("kind", equalTo("owner_required"));
        as("dave").delete(base + "/" + alice).then().statusCode(403);
        as("alice").body(Map.of("role", "admin")).put(base + "/" + alice).then().statusCode(409).body("kind", equalTo("last_owner"));
        as("alice").delete(base + "/" + alice).then().statusCode(409).body("kind", equalTo("last_owner"));
        // Owner is never granted through the API
        as("alice").body(Map.of("role", "owner")).put(base + "/" + dave).then().statusCode(422);

        as("alice").body(Map.of("role", "viewer")).put(base + "/" + dave).then().statusCode(200).body("role", equalTo("viewer"));
        as("dave").get(base).then().statusCode(403);
        as("dave").get("/api/v1/tenants/" + tenant).then().statusCode(200);

        as("alice").delete(base + "/" + dave).then().statusCode(204);
        as("dave").get("/api/v1/tenants/" + tenant).then().statusCode(404);
        as("alice").delete(base + "/" + dave).then().statusCode(404);
        // Another tenant's members are invisible
        as("alice").delete("/api/v1/tenants/" + newTenant() + "/members/" + alice).then().statusCode(404);
    }

    @Test
    void member_creates_replay_with_an_idempotency_key() {
        UUID tenant = newTenant();
        String base = "/api/v1/tenants/" + tenant + "/members";
        String key = UUID.randomUUID().toString();
        Map<String, String> dave = Map.of("kind", "user", "subject", subject("dave"), "role", "operator");
        String first = as("alice").header("Idempotency-Key", key).body(dave).post(base).then().statusCode(201).extract().path("id");
        as("alice").header("Idempotency-Key", key).body(dave).post(base).then().statusCode(201).body("id", equalTo(first));
        as("alice").header("Idempotency-Key", key).body(Map.of("kind", "user", "subject", "otro", "role", "viewer")).post(base)
                .then().statusCode(422).body("kind", equalTo("idempotency_key_reused"));
        // Without a key a second add is a conflict
        as("alice").body(dave).post(base).then().statusCode(409).body("kind", equalTo("member_exists"));
        // An operator can't use a key to get past the role check
        as("dave").header("Idempotency-Key", UUID.randomUUID().toString()).body(Map.of("kind", "client", "subject", "x", "role", "viewer"))
                .post(base).then().statusCode(403);
    }

    @Test
    void admins_rename_the_tenant_and_read_its_notification_email() {
        UUID tenant = newTenant();
        String base = "/api/v1/tenants/" + tenant;
        as("alice").body(Map.of("name", "  Nombre nuevo  ")).put(base).then().statusCode(200).body("name", equalTo("Nombre nuevo"));
        as("alice").get(base).then().body("name", equalTo("Nombre nuevo"));
        as("alice").get("/api/v1/tenants").then().body("find { it.id == '" + tenant + "' }.name", equalTo("Nombre nuevo"));
        as("alice").body(Map.of("name", " ")).put(base).then().statusCode(422);

        as("alice").get(base + "/notification-settings").then().statusCode(200).body("notification_email", nullValue());
        as("alice").body(Map.of("notification_email", "avisos@example.com")).put(base + "/notification-settings").then().statusCode(200)
                .body("notification_email", equalTo("avisos@example.com"));
        as("alice").get(base + "/notification-settings").then().body("notification_email", equalTo("avisos@example.com"));

        // Viewers read, but don't rename
        as("alice").body(Map.of("kind", "user", "subject", subject("dave"), "role", "viewer")).post(base + "/members").then().statusCode(201);
        as("dave").get(base + "/notification-settings").then().statusCode(200);
        as("dave").body(Map.of("name", "Otro")).put(base).then().statusCode(403);
    }

    @Test
    void series_move_forward_only_and_a_deactivated_series_refuses_emits() {
        UUID tenant = newTenant();
        UUID company = companies.verified(tenant, "fake");
        String base = "/api/v1/tenants/" + tenant + "/companies/" + company;
        String series = as("alice").get(base + "/series").then().statusCode(200).extract().path("find { it.serie == 'F001' }.id");

        as("alice").body(invoice()).post(base + "/documents").then().statusCode(201).body("serie_numero", equalTo("F001-1"));
        // Numbers already used are never reused
        as("alice").body(Map.of("next_number", 1)).put(base + "/series/" + series).then().statusCode(409)
                .body("kind", equalTo("series_number_used"));
        as("alice").body(Map.of("next_number", 500)).put(base + "/series/" + series).then().statusCode(200)
                .body("next_number", equalTo(500)).body("active", equalTo(true));
        as("alice").body(invoice()).post(base + "/documents").then().statusCode(201).body("serie_numero", equalTo("F001-500"));

        as("alice").body(Map.of("active", false)).put(base + "/series/" + series).then().statusCode(200).body("active", equalTo(false));
        as("alice").body(invoice()).post(base + "/documents").then().statusCode(422).body("kind", equalTo("series_not_found"));
        as("alice").body(Map.of("active", true)).put(base + "/series/" + series).then().statusCode(200);
        as("alice").body(invoice()).post(base + "/documents").then().statusCode(201).body("serie_numero", equalTo("F001-501"));

        as("alice").body(Map.of("next_number", 0)).put(base + "/series/" + series).then().statusCode(422);
        as("alice").body(Map.of("active", false)).put(base + "/series/" + UUID.randomUUID()).then().statusCode(404);
    }

    @Test
    void company_and_series_creates_replay_with_an_idempotency_key() {
        UUID tenant = newTenant();
        String base = "/api/v1/tenants/" + tenant + "/companies";
        String key = UUID.randomUUID().toString();
        Map<String, String> company = Map.of("ruc", randomRuc(), "razon_social", "Empresa idempotente");
        String id = as("alice").header("Idempotency-Key", key).body(company).post(base).then().statusCode(201).extract().path("id");
        as("alice").header("Idempotency-Key", key).body(company).post(base).then().statusCode(201).body("id", equalTo(id));
        as("alice").get(base).then().body("items.id", hasSize(1));

        String seriesKey = UUID.randomUUID().toString();
        Map<String, Object> series = Map.of("tipo_comprobante", "01", "serie", "F777");
        String created = as("alice").header("Idempotency-Key", seriesKey).body(series).post(base + "/" + id + "/series")
                .then().statusCode(201).extract().path("id");
        as("alice").header("Idempotency-Key", seriesKey).body(series).post(base + "/" + id + "/series")
                .then().statusCode(201).body("id", equalTo(created));
        // The same key for a different create is refused
        as("alice").header("Idempotency-Key", seriesKey).body(company).post(base).then().statusCode(422);
    }

    @Test
    void the_company_view_returns_its_email_texts() {
        UUID tenant = newTenant();
        UUID company = companies.verified(tenant, "fake");
        String base = "/api/v1/tenants/" + tenant + "/companies/" + company;
        as("alice").get(base).then().statusCode(200).body("email_subject", nullValue()).body("email_body", nullValue());
        as("alice").body(Map.of("email_subject", "Su comprobante {serie_numero}", "email_body", "Gracias por su compra."))
                .put(base + "/settings").then().statusCode(200).body("email_subject", equalTo("Su comprobante {serie_numero}"));
        as("alice").get(base).then().body("email_subject", equalTo("Su comprobante {serie_numero}"))
                .body("email_body", equalTo("Gracias por su compra."));
    }

    @Test
    void partners_read_their_branding_and_upload_a_logo() throws Exception {
        UUID partner = newPartner();
        String base = "/api/v1/partners/" + partner;
        as("alice").get(base + "/branding").then().statusCode(200).body("partner_id", equalTo(partner.toString()))
                .body("has_logo", equalTo(false)).body("branding.color", nullValue());
        as("alice").body(Map.of("color", "#aa0000", "footer", "Por Partner", "sender_name", "Partner"))
                .put(base + "/branding").then().statusCode(200).body("branding.color", equalTo("#aa0000"));
        as("alice").get(base + "/branding").then().body("branding.footer", equalTo("Por Partner"))
                .body("branding.sender_name", equalTo("Partner"));

        String token = new KeycloakTestClient().getAccessToken("alice");
        given().auth().oauth2(token).contentType("image/png").body(png()).put(base + "/logo").then().statusCode(200)
                .body("has_logo", equalTo(true)).body("branding.color", equalTo("#aa0000"));
        given().auth().oauth2(token).contentType("image/png").body("no es una imagen".getBytes(StandardCharsets.UTF_8))
                .put(base + "/logo").then().statusCode(422);
        as("alice").delete(base + "/logo").then().statusCode(204);
        as("alice").get(base + "/branding").then().body("has_logo", equalTo(false));

        // Other people see nothing
        as("bob").get(base + "/branding").then().statusCode(404);
        given().auth().oauth2(new KeycloakTestClient().getAccessToken("bob")).contentType("image/png").body(png())
                .put(base + "/logo").then().statusCode(404);
    }

    static byte[] png() throws Exception {
        var image = new java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var out = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    void sire_periods_are_listed_latest_first() {
        UUID tenant = newTenant();
        UUID company = companies.verified(tenant, "fake");
        QuarkusTransaction.requiringNew().run(() -> {
            access.enterAsPlatform(tenant);
            for (String[] p : List.of(new String[] {"rvie", "202601"}, new String[] {"rce", "202602"}, new String[] {"rvie", "202602"})) {
                em.createNativeQuery("""
                                INSERT INTO xhandler.sire_period (id, tenant_id, company_id, sunat_env, book, period, status, requested_by)
                                VALUES (gen_random_uuid(), :t, :c, 'fake', :b, :p, 'requested', 'test')""")
                        .setParameter("t", tenant).setParameter("c", company).setParameter("b", p[0]).setParameter("p", p[1]).executeUpdate();
            }
        });
        String base = "/api/v1/tenants/" + tenant + "/companies/" + company + "/sire";
        as("alice").get(base).then().statusCode(200).body("items.period", contains("202602", "202602", "202601"))
                .body("items.book", contains("rce", "rvie", "rvie")).body("items[0].status", equalTo("requested"));
        as("alice").get(base + "?book=rvie&limit=1").then().statusCode(200).body("items.period", contains("202602")).body("limit", equalTo(1));
        as("alice").get(base + "?book=otro").then().statusCode(422);
        as("alice").get(base + "/rvie/202601").then().statusCode(200).body("book", equalTo("rvie"));
        as("bob").get(base).then().statusCode(404);
    }

    @Test
    void webhooks_are_read_by_id_and_creates_replay_without_the_secret() throws Exception {
        try (var receiver = new io.github.projectopenubl.xhandler.documents.WebhookReceiver()) {
            webhooks(receiver.url());
        }
    }

    void webhooks(String url) {
        UUID tenant = newTenant();
        companies.verified(tenant, "fake");
        String base = "/api/v1/tenants/" + tenant + "/webhooks";
        String key = UUID.randomUUID().toString();
        Map<String, Object> webhook = Map.of("url", url, "events", List.of("document.*"));
        String id = as("alice").header("Idempotency-Key", key).body(webhook).post(base).then().statusCode(201)
                .body("secret", startsWith("whsec_")).extract().path("id");
        // The secret is shown once: the replay doesn't carry it
        as("alice").header("Idempotency-Key", key).body(webhook).post(base).then().statusCode(201)
                .body("id", equalTo(id)).body("secret", nullValue());
        as("alice").get(base).then().body("id", hasSize(1));

        as("alice").get(base + "/" + id).then().statusCode(200).body("url", equalTo(url))
                .body("events", contains("document.*")).body("secret", nullValue());
        as("alice").delete(base + "/" + id).then().statusCode(204);
        as("alice").get(base + "/" + id).then().statusCode(404);
        as("bob").get(base + "/" + id).then().statusCode(404);
    }

    @Test
    void documents_are_filtered_by_buyer() {
        UUID tenant = newTenant();
        UUID company = companies.verified(tenant, "fake");
        String dni = String.valueOf(10_000_000 + ThreadLocalRandom.current().nextInt(89_999_999));
        String mine = docs.pending(tenant, company, "fake:invoice", d -> {
            d.customerTipo = "1";
            d.customerNumero = dni;
            d.customerNombre = "Juana 100% Pérez";
        }).id().toString();
        String other = docs.pending(tenant, company, "fake:invoice", d -> {
            d.customerTipo = "6";
            d.customerNumero = "20100000001";
            d.customerNombre = "Juanito SAC";
        }).id().toString();
        String base = "/api/v1/tenants/" + tenant + "/documents";

        as("alice").queryParam("customer_numero", dni).get(base).then().statusCode(200).body("items.id", contains(mine));
        as("alice").queryParam("customer_nombre", "juan").get(base).then().body("items.id", hasItems(mine, other));
        as("alice").queryParam("customer_nombre", "JUANA 100%").get(base).then().body("items.id", contains(mine));
        // LIKE wildcards in the term are literal
        as("alice").queryParam("customer_nombre", "juan_").get(base).then().body("items.id", hasSize(0));
        as("alice").queryParam("customer_nombre", "pérez").get(base).then().body("items.id", not(hasItem(mine)));
    }

    @Test
    void status_reports_sunat_per_environment() {
        given().get("/api/v1/status").then().statusCode(200)
                .body("sunat.environment", contains("production", "beta", "fake"))
                .body("sunat.find { it.environment == 'beta' }.available", notNullValue())
                .body("sunat.find { it.environment == 'beta' }.endpoints", notNullValue());
    }

    static Map<String, Object> invoice() {
        Map<String, Object> spec = new HashMap<>(Map.of(
                "cliente", Map.of("tipo_documento_identidad", "6", "numero_documento_identidad", "12121212121", "nombre", "Cliente SAC"),
                "detalles", List.of(Map.of("descripcion", "Item", "cantidad", 10, "precio", 100))));
        return Map.of("document", Map.of("kind", "Invoice", "spec", spec));
    }
}
