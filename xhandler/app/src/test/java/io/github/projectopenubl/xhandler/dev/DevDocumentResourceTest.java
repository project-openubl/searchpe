package io.github.projectopenubl.xhandler.dev;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class DevDocumentResourceTest {

    @Inject
    FakeEngine engine;

    @BeforeEach
    void reset() {
        engine.reset();
    }

    @Test
    void builds_signs_and_sends_to_beta() {
        given().contentType("application/json")
                .body("{\"kind\":\"Invoice\",\"spec\":{\"serie_numero\":\"F001-1\"}}")
                .when()
                .post("/api/v1/dev/documents")
                .then()
                .statusCode(200)
                .body("digest_value", equalTo("digest-123"))
                .body("sunat.status", equalTo("accepted"))
                .body("sunat.cdr_zip", equalTo("UEsDBA=="));

        assertEquals(List.of("build", "sign", "send:beta"), engine.calls());
    }

    @Test
    void invalid_document_is_422_and_nothing_is_sent() {
        given().contentType("application/json")
                .body("{\"kind\":\"Invoice\",\"spec\":{}}")
                .when()
                .post("/api/v1/dev/documents")
                .then()
                .statusCode(422)
                .body("kind", equalTo("invalid_document"));

        assertEquals(List.of("build"), engine.calls());
    }

    @Test
    void health_is_up() {
        given().when().get("/q/health").then().statusCode(200).body("status", equalTo("UP"));
    }
}
