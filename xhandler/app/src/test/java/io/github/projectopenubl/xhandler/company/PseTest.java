package io.github.projectopenubl.xhandler.company;

import static io.github.projectopenubl.xhandler.Api.as;
import static io.github.projectopenubl.xhandler.Api.tenantOf;
import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.projectopenubl.xhandler.engine.EngineStub;
import io.github.projectopenubl.xhandler.keys.TestCertificates;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import jakarta.inject.Inject;
import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** P7: the platform as PSE signs with its certificate and sends with its pool of SOL users. */
@QuarkusTest
@QuarkusTestResource(EngineStub.class)
class PseTest {

    @Inject
    TestCompanies companies;

    @AfterEach
    void tearDown() {
        EngineStub.reset();
    }

    static Map<String, Object> invoice() {
        return Map.of("document", Map.of("kind", "Invoice", "spec", Map.of(
                "cliente", Map.of("tipo_documento_identidad", "6", "numero_documento_identidad", "20111111111", "nombre", "X"),
                "detalles", List.of())));
    }

    @Test
    void the_platform_signs_and_sends_as_pse() throws Exception {
        // The platform's PSE certificate and two SOL users
        String platformRuc = io.github.projectopenubl.xhandler.Api.randomRuc();
        given().auth().oauth2(new KeycloakTestClient().getAccessToken("olga"))
                .multiPart("file", TestCertificates.generate(platformRuc), "application/x-pkcs12").multiPart("password", TestCertificates.PASSWORD)
                .post("/api/admin/v1/pse/certificate").then().statusCode(200).body("ruc", equalTo(platformRuc));
        String user = platformRuc + "PSE" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        as("olga").body(Map.of("username", user, "password", "pse-secret")).post("/api/admin/v1/pse/sol-users").then().statusCode(200);
        List<String> pool = as("olga").get("/api/admin/v1/pse/sol-users").then().statusCode(200).extract().path("id");
        // Only this test's user stays active, so the send must use it
        for (Map<String, Object> entry : as("olga").get("/api/admin/v1/pse/sol-users").then().extract().jsonPath().<Map<String, Object>>getList("$")) {
            if (!user.equals(entry.get("label")) && Boolean.TRUE.equals(entry.get("active"))) {
                as("olga").post("/api/admin/v1/pse/sol-users/" + entry.get("id") + "/deactivate").then().statusCode(200);
            }
        }
        assertTrue(pool.size() >= 1);

        UUID tenant = tenantOf("alice");
        UUID company = companies.verified(tenant, "production");
        String base = "/api/v1/tenants/" + tenant + "/companies/" + company;
        as("alice").body(Map.of("emission_mode", "pse")).put(base + "/pse/mode").then().statusCode(409).body("kind", equalTo("pse_not_authorized"));
        as("alice").body(Map.of("authorized_at", io.github.projectopenubl.xhandler.documents.Deadlines.todayInLima().toString(),
                "evidence_base64", Base64.getEncoder().encodeToString("captura de SOL".getBytes()))).post(base + "/pse/authorization")
                .then().statusCode(200);
        as("alice").body(Map.of("emission_mode", "pse")).put(base + "/pse/mode").then().statusCode(200).body("emission_mode", equalTo("pse"));

        // No company certificate and no company SOL user: the platform's are used
        String id = as("alice").body(invoice()).post(base + "/documents").then().statusCode(201).extract().path("id");
        await().atMost(Duration.ofSeconds(20)).until(() -> "accepted".equals(
                as("alice").get("/api/v1/tenants/" + tenant + "/documents/" + id).then().extract().path("status")));
        var finalize = new ObjectMapper().readTree(EngineStub.lastBody("/v1/sign/finalize"));
        X509Certificate signer = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(Base64.getDecoder().decode(finalize.get("certificate").asText())));
        assertTrue(signer.getSubjectX500Principal().getName().contains(platformRuc), "signed with the platform's PSE certificate");
        assertTrue(EngineStub.lastBody("/v1/send").contains(user), "sent with the platform's SOL user");

        as("olga").get("/api/admin/v1/pse/rejected-rate").then().statusCode(200).body("limit", equalTo(0.10f));

        // Revoking goes back to software mode and tells the tenant
        as("alice").delete(base + "/pse/authorization").then().statusCode(200).body("emission_mode", equalTo("software"));
        as("alice").get("/api/v1/tenants/" + tenant + "/notifications").then().body("items.kind", hasItem("pse.authorization_revoked"));
    }
}
