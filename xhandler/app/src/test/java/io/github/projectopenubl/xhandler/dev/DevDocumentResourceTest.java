package io.github.projectopenubl.xhandler.dev;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.projectopenubl.xhandler.engine.EngineStub;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(EngineStub.class)
class DevDocumentResourceTest {

    @BeforeEach
    void reset() {
        EngineStub.reset();
    }

    @Test
    void builds_signs_with_demo_key_and_sends_to_beta() {
        given().contentType("application/json")
                .body("{\"kind\":\"Invoice\",\"spec\":{\"serie_numero\":\"F001-1\"}}")
                .when()
                .post("/api/v1/dev/documents")
                .then()
                .statusCode(200)
                .body("digest_value", equalTo("digest-123"))
                .body("sunat.status", equalTo("accepted"))
                .body("sunat.code", equalTo(0))
                .body("sunat.cdr_zip", equalTo("UEsDBA=="));

        List<EngineStub.Recorded> requests = EngineStub.requests();
        assertEquals(List.of("/v1/build", "/v1/sign", "/v1/send"), requests.stream().map(EngineStub.Recorded::path).toList());
        // Wire format: snake_case, explicit demo key, lowercase environment
        assertTrue(requests.get(0).body().contains("\"document\":{\"kind\":\"Invoice\""), requests.get(0).body());
        assertTrue(requests.get(1).body().contains("\"demo_key\":true"), requests.get(1).body());
        assertTrue(requests.get(1).body().contains("\"xml\":\"<Invoice/>\""), requests.get(1).body());
        assertTrue(requests.get(2).body().contains("\"environment\":\"beta\""), requests.get(2).body());
        assertTrue(requests.get(2).body().contains("\"xml\":\"<Invoice signed/>\""), requests.get(2).body());
    }

    @Test
    void invalid_document_is_422_in_spanish_and_nothing_is_sent() {
        given().contentType("application/json")
                .body("{\"kind\":\"Invoice\",\"spec\":{}}")
                .when()
                .post("/api/v1/dev/documents")
                .then()
                .statusCode(422)
                .body("kind", equalTo("invalid_document"))
                .body("message", equalTo("El documento no es válido."))
                .body("detail", equalTo("missing field `serie_numero`"));

        assertEquals(List.of("/v1/build"), EngineStub.requests().stream().map(EngineStub.Recorded::path).toList());
    }

    @Test
    void health_is_up() {
        given().when().get("/q/health").then().statusCode(200).body("status", equalTo("UP"));
    }
}
