package io.github.projectopenubl.xhandler;

import static io.restassured.RestAssured.given;

import io.quarkus.test.keycloak.client.KeycloakTestClient;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.eclipse.microprofile.config.ConfigProvider;

/** Test helpers: real Keycloak tokens (Dev Services) and common API calls. */
public final class Api {

    private static final KeycloakTestClient KEYCLOAK = new KeycloakTestClient();

    private Api() {}

    /** A person from the dev realm (password = username). */
    public static RequestSpecification as(String user) {
        return given().auth().oauth2(KEYCLOAK.getAccessToken(user)).contentType("application/json");
    }

    /** The acme-erp machine client (client credentials). */
    public static RequestSpecification asAcmeErp() {
        String authServer = ConfigProvider.getConfig().getValue("quarkus.oidc.auth-server-url", String.class);
        String token = given().formParams(Map.of(
                        "grant_type", "client_credentials", "client_id", "acme-erp", "client_secret", "acme-secret"))
                .post(authServer + "/protocol/openid-connect/token")
                .then()
                .statusCode(200)
                .extract()
                .path("access_token");
        return given().auth().oauth2(token).contentType("application/json");
    }

    /** The user's tenant, created on first use (one free tenant per person). */
    public static UUID tenantOf(String user) {
        List<String> ids = as(user).get("/api/v1/tenants").then().statusCode(200).extract().path("id");
        if (!ids.isEmpty()) {
            return UUID.fromString(ids.get(0));
        }
        String id = as(user).body(Map.of("name", "Cuenta de " + user)).post("/api/v1/tenants")
                .then().statusCode(201).extract().path("id");
        return UUID.fromString(id);
    }

    /** A random RUC that won't collide between tests (RUCs are unique on the platform). */
    public static String randomRuc() {
        return "20" + String.format("%09d", ThreadLocalRandom.current().nextInt(1_000_000_000));
    }

    public static UUID createCompany(String user, UUID tenantId, String ruc) {
        Response response = as(user).body(Map.of("ruc", ruc, "razon_social", "Empresa " + ruc))
                .post("/api/v1/tenants/" + tenantId + "/companies");
        response.then().statusCode(201);
        return UUID.fromString(response.path("id"));
    }
}
