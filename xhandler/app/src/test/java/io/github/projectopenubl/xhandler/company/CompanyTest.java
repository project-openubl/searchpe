package io.github.projectopenubl.xhandler.company;

import static io.github.projectopenubl.xhandler.Api.as;
import static io.github.projectopenubl.xhandler.Api.createCompany;
import static io.github.projectopenubl.xhandler.Api.randomRuc;
import static io.github.projectopenubl.xhandler.Api.tenantOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;

import io.github.projectopenubl.xhandler.engine.EngineStub;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(EngineStub.class)
class CompanyTest {

    @jakarta.inject.Inject
    io.github.projectopenubl.xhandler.tenancy.TenantAccess access;

    @Test
    void new_company_starts_in_the_sandbox_with_default_series() {
        UUID tenant = tenantOf("alice");
        String ruc = randomRuc();
        UUID company = createCompany("alice", tenant, ruc);

        as("alice").get("/api/v1/tenants/" + tenant + "/companies/" + company).then().statusCode(200)
                .body("ruc", equalTo(ruc))
                .body("sunat_env", equalTo("fake"))
                .body("emission_mode", equalTo("software"));
        as("alice").get("/api/v1/tenants/" + tenant + "/companies/" + company + "/series").then().statusCode(200)
                .body("$", hasSize(10))
                .body("serie", hasItems("F001", "B001", "FC01", "BC01", "FD01", "BD01", "T001", "V001", "P001", "R001"))
                .body("find { it.serie == 'F001' }.tipo_comprobante", equalTo("01"))
                .body("find { it.serie == 'F001' }.next_number", equalTo(1));
    }

    @Test
    void a_ruc_is_claimed_only_when_verified() {
        // Typing a RUC doesn't claim it: two tenants may register it unverified
        String ruc = randomRuc();
        UUID alices = createCompany("alice", tenantOf("alice"), ruc);
        UUID bobs = createCompany("bob", tenantOf("bob"), ruc);

        // Verifying claims it (P3: SOL login); a second verification of the same RUC fails
        verify(tenantOf("alice"), alices);
        RuntimeException duplicate = org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> verify(tenantOf("bob"), bobs));
        org.junit.jupiter.api.Assertions.assertEquals("company_ruc_env_verified",
                io.github.projectopenubl.xhandler.api.Constraints.violated(duplicate).orElseThrow());
    }

    @Test
    void unverified_companies_per_tenant_are_limited() {
        UUID tenant = tenantOf("carol");
        int created = 0;
        io.restassured.response.Response response;
        do {
            response = as("carol").body(Map.of("ruc", randomRuc(), "razon_social", "Empresa"))
                    .post("/api/v1/tenants/" + tenant + "/companies");
            created++;
        } while (response.statusCode() == 201 && created <= 201);
        response.then().statusCode(409).body("kind", equalTo("limit_reached"));
    }

    private void verify(UUID tenant, UUID company) {
        io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> {
            access.enterAsPlatform(tenant);
            Company c = Company.findById(company);
            c.rucVerifiedAt = java.time.Instant.now();
            Company.flush();
        });
    }

    @Test
    void invalid_company_is_422_in_spanish() {
        as("alice").body(Map.of("ruc", "123", "razon_social", ""))
                .post("/api/v1/tenants/" + tenantOf("alice") + "/companies")
                .then().statusCode(422)
                .body("kind", equalTo("invalid_request"))
                .body("message", equalTo("La solicitud tiene datos inválidos."))
                .body("detail", containsString("ruc"));
    }

    @Test
    void series_can_continue_another_providers_numbering() {
        UUID tenant = tenantOf("alice");
        UUID company = createCompany("alice", tenant, randomRuc());
        String path = "/api/v1/tenants/" + tenant + "/companies/" + company + "/series";

        as("alice").body(Map.of("tipo_comprobante", "01", "serie", "F002", "next_number", 1500)).post(path)
                .then().statusCode(201).body("next_number", equalTo(1500));
        as("alice").body(Map.of("tipo_comprobante", "01", "serie", "F002")).post(path)
                .then().statusCode(409).body("kind", equalTo("series_exists"));
    }

    @Test
    void companies_are_paged() {
        UUID tenant = tenantOf("alice");
        createCompany("alice", tenant, randomRuc());
        createCompany("alice", tenant, randomRuc());
        as("alice").get("/api/v1/tenants/" + tenant + "/companies?limit=1").then().statusCode(200)
                .body("items", hasSize(1))
                .body("limit", equalTo(1))
                .body("offset", equalTo(0));
    }
}
