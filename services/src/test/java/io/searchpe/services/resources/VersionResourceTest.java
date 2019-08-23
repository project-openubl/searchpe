package io.searchpe.services.resources;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;

@QuarkusTest
public class VersionResourceTest {

    @Test
    void getActiveVersion() {
        //List all, should have all 3 fruits the database has initially:
        given()
                .when().get("/versions/current")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body(
                        containsString("\"id\":1")
                );
    }

    @Test
    void getVersionById() {
        //not found
        given()
                .when().get("/versions/0")
                .then()
                .statusCode(Response.Status.NOT_FOUND.getStatusCode());

        //List all, should have all 3 fruits the database has initially:
        given()
                .when().get("/versions/1")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body(
                        containsString("\"id\":1")
                );
    }
}
