package io.github.project.openubl.searchpe.resources;

import io.github.project.openubl.searchpe.models.jpa.entity.Status;
import io.github.project.openubl.searchpe.models.jpa.entity.VersionEntity;
import io.github.project.openubl.searchpe.resources.config.PostgreSQLServer;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.NativeImageTest;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

@NativeImageTest
@QuarkusTestResource(PostgreSQLServer.class)
public class NativeResourceIT {

    @Test
    @Order(1)
    public void importData() {
        // Given

        // When
        VersionEntity versionCreated = given()
                .header("Content-Type", "application/json")
                .when()
                .post("/versions")
                .then()
                .statusCode(200)
                .body(notNullValue())
                .extract()
                .as(VersionEntity.class);

        // Then
        await()
                .atMost(10, TimeUnit.MINUTES)
                .until(() -> {

                    VersionEntity versionWatched = given()
                            .header("Content-Type", "application/json")
                            .when()
                            .get("/versions/" + versionCreated.id)
                            .then()
                            .extract()
                            .as(VersionEntity.class);

                    return versionWatched.status == Status.COMPLETED;
                });
    }

    @Test
    @Order(2)
    public void verifyContribuyentes() {
        given()
                .header("Content-Type", "application/json")
                .when()
                .get("/contribuyentes")
                .then()
                .statusCode(200)
                .body(
                        "meta.offset", is(0),
                        "meta.limit", is(10),
                        "meta.count", is(1000),
                        "data.size()", is(10),
                        "data[0].ruc", is("20272209325")
                );
    }

}
