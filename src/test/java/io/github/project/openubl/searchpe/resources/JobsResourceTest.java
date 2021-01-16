package io.github.project.openubl.searchpe.resources;

import io.github.project.openubl.searchpe.resources.config.PostgreSQLServer;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;

@QuarkusTest
@QuarkusTestResource(PostgreSQLServer.class)

public class JobsResourceTest {

//    @Test
//    public void triggerNewJob() {
//        // Given
//
//        // When
//        given()
//                .header("Content-Type", "application/json")
//                .when()
//                .post("/jobs")
//                .then()
//                .statusCode(200)
//                .extract();
//
//        // Then
////        await()
////                .atMost(Duration.ofMinutes(5))
////                .until(() -> {
////
////                });
//
//
//    }

}
