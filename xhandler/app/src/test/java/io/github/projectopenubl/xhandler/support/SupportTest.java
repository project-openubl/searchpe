package io.github.projectopenubl.xhandler.support;

import static io.github.projectopenubl.xhandler.Api.as;
import static io.github.projectopenubl.xhandler.Api.createCompany;
import static io.github.projectopenubl.xhandler.Api.randomRuc;
import static io.github.projectopenubl.xhandler.Api.tenantOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import io.github.projectopenubl.xhandler.engine.EngineStub;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(EngineStub.class)
class SupportTest {

    @Test
    void operators_get_read_only_access_only_while_a_session_is_approved() {
        UUID tenant = tenantOf("alice");
        createCompany("alice", tenant, randomRuc());
        String companies = "/api/v1/tenants/" + tenant + "/companies";

        as("carol").get(companies).then().statusCode(404);

        String session = as("carol").body(Map.of("tenant_id", tenant, "reason", "Ticket #123: error al emitir"))
                .post("/api/v1/support/sessions").then().statusCode(201).extract().path("id");
        as("carol").get(companies).then().statusCode(404); // not approved yet

        as("alice").post("/api/v1/tenants/" + tenant + "/support-sessions/" + session + "/approve")
                .then().statusCode(200).body("expires_at", notNullValue());
        as("carol").get(companies).then().statusCode(200);
        // Every access by the operator is in the tenant's audit log
        as("alice").get("/api/v1/tenants/" + tenant + "/audit").then().statusCode(200)
                .body("items.find { it.action == 'support.access' }.details.path", equalTo(companies))
                .body("items.find { it.action == 'support_session.requested' }.actor", org.hamcrest.Matchers.startsWith("user:"));
        // Read-only
        as("carol").body(Map.of("ruc", randomRuc(), "razon_social", "X")).post(companies)
                .then().statusCode(403);

        as("alice").post("/api/v1/tenants/" + tenant + "/support-sessions/" + session + "/revoke").then().statusCode(200);
        as("carol").get(companies).then().statusCode(404);
    }

    @Test
    void only_platform_operators_can_request_sessions() {
        as("bob").body(Map.of("tenant_id", tenantOf("alice"), "reason", "curiosidad"))
                .post("/api/v1/support/sessions").then().statusCode(403).body("kind", equalTo("forbidden"));
    }
}
