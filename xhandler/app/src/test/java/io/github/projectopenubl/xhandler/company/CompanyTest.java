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

    @Test
    void new_company_starts_in_beta_with_default_series() {
        UUID tenant = tenantOf("alice");
        String ruc = randomRuc();
        UUID company = createCompany("alice", tenant, ruc);

        as("alice").get("/api/v1/tenants/" + tenant + "/companies/" + company).then().statusCode(200)
                .body("ruc", equalTo(ruc))
                .body("sunat_env", equalTo("beta"))
                .body("emission_mode", equalTo("software"));
        as("alice").get("/api/v1/tenants/" + tenant + "/companies/" + company + "/series").then().statusCode(200)
                .body("$", hasSize(10))
                .body("serie", hasItems("F001", "B001", "FC01", "BC01", "FD01", "BD01", "T001", "V001", "P001", "R001"))
                .body("find { it.serie == 'F001' }.tipo_comprobante", equalTo("01"))
                .body("find { it.serie == 'F001' }.next_number", equalTo(1));
    }

    @Test
    void a_ruc_exists_once_on_the_platform() {
        String ruc = randomRuc();
        createCompany("alice", tenantOf("alice"), ruc);

        as("bob").body(Map.of("ruc", ruc, "razon_social", "Impostor SAC"))
                .post("/api/v1/tenants/" + tenantOf("bob") + "/companies")
                .then().statusCode(409).body("kind", equalTo("ruc_taken"));
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
