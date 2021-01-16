package io.github.project.openubl.searchpe.resources;

import io.github.project.openubl.searchpe.resources.config.PostgreSQLServer;
import io.github.project.openubl.searchpe.models.jpa.VersionRepository;
import io.github.project.openubl.searchpe.models.jpa.entity.Status;
import io.github.project.openubl.searchpe.models.jpa.entity.VersionEntity;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.Date;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
@QuarkusTestResource(PostgreSQLServer.class)
public class VersionResourceTest {

    @Inject
    VersionRepository versionRepository;

    @AfterEach
    public void afterEach() {
        versionRepository.deleteAll();
    }

    @Test
    public void getVersions() {
        // Given
        VersionEntity version1 = VersionEntity.Builder.aVersionEntity()
                .withActive(false)
                .withStatus(Status.ERROR)
                .withCreatedAt(new Date())
                .build();
        VersionEntity version2 = VersionEntity.Builder.aVersionEntity()
                .withActive(true)
                .withStatus(Status.COMPLETED)
                .withCreatedAt(new Date())
                .build();

        versionRepository.persist(version1, version2);

        // When
        given()
                .header("Content-Type", "application/json")
                .when()
                .get("/versions")
                .then()
                .statusCode(200)
                .body(
                        "size()", is(2),
                        "[0].active", is(false),
                        "[0].status", is(Status.ERROR.toString()),
                        "[1].active", is(true),
                        "[1].status", is(Status.COMPLETED.toString())
                );

    }

    @Test
    public void getVersion() {
        // Given
        VersionEntity version = VersionEntity.Builder.aVersionEntity()
                .withActive(false)
                .withStatus(Status.ERROR)
                .withCreatedAt(new Date())
                .build();

        versionRepository.persist(version);

        // When
        given()
                .header("Content-Type", "application/json")
                .when()
                .get("/versions/" + version.id)
                .then()
                .statusCode(200)
                .body(
                        "active", is(false),
                        "status", is(Status.ERROR.toString())
                );

    }

    @Test
    public void getVersion_nonExists() {
        // Given

        // When
        given()
                .header("Content-Type", "application/json")
                .when()
                .get("/versions/1")
                .then()
                .statusCode(404);

    }
}
