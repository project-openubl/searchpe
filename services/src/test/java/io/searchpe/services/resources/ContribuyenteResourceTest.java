package io.searchpe.services.resources;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class ContribuyenteResourceTest {

    @Test
    void getCompanies() {
        //List all, should have all 3 fruits the database has initially:
        given()
                .when().get("/contribuyentes")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body(
                        containsString("\"id\":1"),
                        containsString("\"totalElements\":1")
                );
    }

    @Test
    void getById() {
        //not found
        given()
                .when().get("/contribuyentes/0")
                .then()
                .statusCode(Response.Status.NOT_FOUND.getStatusCode());

        //List all, should have all 3 fruits the database has initially:
        given()
                .when().get("/contribuyentes/1")
                .then()
                .statusCode(Response.Status.OK.getStatusCode())
                .body(
                        containsString("\"id\":1")
                );
    }
}
