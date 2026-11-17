package io.github.projectopenubl.xhandler.tenancy;

import static io.github.projectopenubl.xhandler.Api.as;
import static io.github.projectopenubl.xhandler.Api.asAcmeErp;
import static io.github.projectopenubl.xhandler.Api.createCompany;
import static io.github.projectopenubl.xhandler.Api.randomRuc;
import static io.github.projectopenubl.xhandler.Api.tenantOf;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.projectopenubl.xhandler.company.Company;
import io.github.projectopenubl.xhandler.engine.EngineStub;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(EngineStub.class)
class TenancyTest {

    @Test
    void anonymous_requests_are_rejected() {
        given().get("/api/v1/tenants").then().statusCode(401);
    }

    @Test
    void signup_creates_one_free_tenant_per_person() {
        UUID tenant = tenantOf("alice");
        as("alice").get("/api/v1/tenants").then().statusCode(200)
                .body("id", hasItem(tenant.toString()))
                .body("find { it.id == '" + tenant + "' }.role", equalTo("owner"));

        as("alice").body(Map.of("name", "Otra")).post("/api/v1/tenants")
                .then().statusCode(409).body("kind", equalTo("limit_reached"));
    }

    @Test
    void signup_requires_a_verified_email() {
        as("dave").body(Map.of("name", "Dave SAC")).post("/api/v1/tenants")
                .then().statusCode(403).body("kind", equalTo("email_not_verified"));
    }

    @Test
    void tenants_are_invisible_to_other_people() {
        UUID alices = tenantOf("alice");
        createCompany("alice", alices, randomRuc());
        tenantOf("bob");

        as("bob").get("/api/v1/tenants").then().statusCode(200).body("id", not(hasItem(alices.toString())));
        // Unknown and foreign tenants look the same: 404, nothing leaks
        as("bob").get("/api/v1/tenants/" + alices).then().statusCode(404).body("kind", equalTo("not_found"));
        as("bob").get("/api/v1/tenants/" + alices + "/companies").then().statusCode(404);
        as("bob").get("/api/v1/tenants/" + UUID.randomUUID() + "/companies").then().statusCode(404);
    }

    @Test
    void queries_without_an_open_tenant_see_no_rows() {
        UUID tenant = tenantOf("alice");
        createCompany("alice", tenant, randomRuc());

        // Row-level security fails closed: no tenant set, no rows, even though companies exist
        long visible = QuarkusTransaction.requiringNew().call(() -> Company.count());
        assertEquals(0, visible);
    }

    @jakarta.inject.Inject
    TenantAccess access;

    @jakarta.inject.Inject
    jakarta.persistence.EntityManager em;

    @Test
    void an_open_tenant_cant_read_or_write_another_tenants_rows() {
        UUID alices = tenantOf("alice");
        UUID bobs = tenantOf("bob");
        UUID bobsCompany = createCompany("bob", bobs, randomRuc());

        QuarkusTransaction.requiringNew().run(() -> {
            access.enterAsPlatform(alices);
            org.junit.jupiter.api.Assertions.assertNull(Company.findById(bobsCompany));
        });
        // Writing a row for another tenant violates the policy's WITH CHECK
        RuntimeException e = org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () ->
                QuarkusTransaction.requiringNew().run(() -> {
                    access.enterAsPlatform(alices);
                    Company c = new Company();
                    c.id = io.github.projectopenubl.xhandler.api.Ids.newId();
                    c.tenantId = bobs;
                    c.ruc = randomRuc();
                    c.razonSocial = "Intrusa";
                    c.persistAndFlush();
                }));
        assertTrue(rootMessage(e).contains("row-level security"), rootMessage(e));
    }

    @Test
    void the_app_runs_as_xhandler_app_and_the_audit_log_is_append_only() {
        UUID tenant = tenantOf("alice");
        QuarkusTransaction.requiringNew().run(() -> {
            access.enterAsPlatform(tenant);
            Object[] who = (Object[]) em.createNativeQuery(
                            "SELECT current_user, (SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname = current_user)")
                    .getSingleResult();
            assertEquals("xhandler_app", who[0]);
            assertEquals(false, who[1]);
        });
        RuntimeException e = org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () ->
                QuarkusTransaction.requiringNew().run(() -> {
                    access.enterAsPlatform(tenant);
                    em.createNativeQuery("UPDATE xhandler.audit_log SET action = 'x'").executeUpdate();
                }));
        assertTrue(rootMessage(e).contains("permission denied"), rootMessage(e));
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return String.valueOf(t.getMessage());
    }

    @Test
    void machine_clients_act_with_their_role() {
        UUID tenant = tenantOf("alice");
        as("alice").body(Map.of("kind", "client", "subject", "acme-erp", "role", "operator"))
                .post("/api/v1/tenants/" + tenant + "/members")
                .then().statusCode(201);

        asAcmeErp().get("/api/v1/tenants/" + tenant + "/companies").then().statusCode(200);
        // Operators can't administer the tenant
        asAcmeErp().body(Map.of("ruc", randomRuc(), "razon_social", "X")).post("/api/v1/tenants/" + tenant + "/companies")
                .then().statusCode(403).body("kind", equalTo("forbidden"));
        // Machine clients can't sign up
        asAcmeErp().body(Map.of("name", "Robot")).post("/api/v1/tenants").then().statusCode(403);

        as("alice").body(Map.of("kind", "client", "subject", "acme-erp", "role", "operator"))
                .post("/api/v1/tenants/" + tenant + "/members")
                .then().statusCode(409).body("kind", equalTo("member_exists"));
    }

    @Test
    void roles_allow_by_rank_and_support_only_reads() {
        assertTrue(Role.OWNER.allows(Role.ADMIN));
        assertTrue(Role.OPERATOR.allows(Role.VIEWER));
        assertTrue(!Role.VIEWER.allows(Role.OPERATOR));
        assertTrue(Role.SUPPORT.allows(Role.VIEWER));
        assertTrue(!Role.SUPPORT.allows(Role.OPERATOR));
    }
}
