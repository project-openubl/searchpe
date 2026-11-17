package io.github.projectopenubl.xhandler.company;

import static io.github.projectopenubl.xhandler.Api.as;
import static io.github.projectopenubl.xhandler.Api.createCompany;
import static io.github.projectopenubl.xhandler.Api.randomRuc;
import static io.github.projectopenubl.xhandler.Api.tenantOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

import io.github.projectopenubl.xhandler.engine.EngineStub;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Sandbox first, beta on request, production only once verified (DESIGN.md §6, §26.2). */
@QuarkusTest
@QuarkusTestResource(EngineStub.class)
class EnvironmentTest {

    @AfterEach
    void tearDown() {
        EngineStub.reset();
    }

    @Test
    void companies_start_on_the_fake_and_move_to_beta() {
        UUID tenant = tenantOf("alice");
        String ruc = randomRuc();
        UUID company = createCompany("alice", tenant, ruc);
        String path = "/api/v1/tenants/" + tenant + "/companies/" + company;
        as("alice").get(path).then().statusCode(200).body("sunat_env", equalTo("fake"));

        as("alice").body(Map.of("sunat_env", "beta")).post(path + "/environment").then().statusCode(200)
                .body("sunat_env", equalTo("beta"));
        as("alice").get(path + "/series").then().statusCode(200).body("serie", hasItem("F001"))
                .body("sunat_env", hasItem("beta"));
        // Production needs a verified RUC, a certificate and SOL credentials
        as("alice").body(Map.of("sunat_env", "production")).post(path + "/environment").then().statusCode(409)
                .body("kind", equalTo("ruc_not_verified"));
    }

    @Test
    void a_sol_login_verifies_the_ruc() {
        UUID tenant = tenantOf("alice");
        String ruc = randomRuc();
        UUID company = createCompany("alice", tenant, ruc);
        String path = "/api/v1/tenants/" + tenant + "/companies/" + company;

        as("alice").post(path + "/verify").then().statusCode(409).body("kind", equalTo("no_credentials"));
        as("alice").body(Map.of("username", "20000000001USER", "password", "x")).put(path + "/credentials/sol").then().statusCode(200);
        as("alice").post(path + "/verify").then().statusCode(422).body("kind", equalTo("sol_user_mismatch"));

        as("alice").body(Map.of("username", ruc + "USER", "password", "bad")).put(path + "/credentials/sol").then().statusCode(200);
        EngineStub.on("/v1/status-cdr", body ->
                EngineStub.Reply.ok("{\"status\":\"unknown\",\"code\":\"0102\",\"description\":\"Usuario o contraseña incorrectos\",\"cdr_zip\":null}"));
        as("alice").post(path + "/verify").then().statusCode(422).body("kind", equalTo("sol_invalid"));

        EngineStub.reset();
        as("alice").post(path + "/verify").then().statusCode(200).body("ruc_verified_at", notNullValue());
    }

    @Test
    void failed_verifications_are_rate_limited_per_ruc() {
        UUID tenant = tenantOf("alice");
        String ruc = randomRuc();
        UUID company = createCompany("alice", tenant, ruc);
        String path = "/api/v1/tenants/" + tenant + "/companies/" + company;
        as("alice").body(Map.of("username", ruc + "USER", "password", "guess")).put(path + "/credentials/sol").then().statusCode(200);
        EngineStub.on("/v1/status-cdr", body ->
                EngineStub.Reply.ok("{\"status\":\"unknown\",\"code\":\"0102\",\"description\":\"Usuario o contraseña incorrectos\",\"cdr_zip\":null}"));
        for (int i = 0; i < 5; i++) {
            as("alice").post(path + "/verify").then().statusCode(422);
        }
        as("alice").post(path + "/verify").then().statusCode(429).body("kind", equalTo("too_many_attempts"));
    }

    @Test
    void settings_change_the_emit_defaults() {
        UUID tenant = tenantOf("alice");
        UUID company = createCompany("alice", tenant, randomRuc());
        as("alice").body(Map.of("delivered_on_emit_default", false, "email", "facturas@example.com"))
                .put("/api/v1/tenants/" + tenant + "/companies/" + company + "/settings").then().statusCode(200)
                .body("delivered_on_emit_default", equalTo(false))
                .body("email", equalTo("facturas@example.com"));
    }
}
