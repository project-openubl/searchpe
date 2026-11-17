package io.github.projectopenubl.xhandler.admin;

import static io.github.projectopenubl.xhandler.Api.as;
import static io.github.projectopenubl.xhandler.Api.tenantOf;
import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

import io.github.projectopenubl.xhandler.company.TestCompanies;
import io.github.projectopenubl.xhandler.documents.TestDocuments;
import io.github.projectopenubl.xhandler.engine.EngineStub;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(EngineStub.class)
class AdminTest {

    @Inject
    TestDocuments docs;

    @Inject
    TestCompanies companies;

    @Test
    void status_is_public_and_reports_workers_and_versions() {
        given().get("/api/v1/status").then().statusCode(200)
                .body("database", equalTo(true))
                .body("engine", equalTo(true))
                .body("versions.engine", equalTo("0.0.0-stub"))
                .body("workers.find { it.role == 'all' }.online", greaterThanOrEqualTo(1));
    }

    @Test
    void only_platform_admins_reach_the_admin_api() {
        as("alice").get("/api/admin/v1/endpoints").then().statusCode(403);
        given().get("/api/admin/v1/endpoints").then().statusCode(401);
        as("olga").get("/api/admin/v1/migrations").then().statusCode(200)
                .body("version", hasItem("4"))
                .body("find { it.version == '4' }.state", equalTo("Success"));
    }

    @Test
    void admins_hold_release_and_retry_documents_with_an_audit_trail() {
        UUID tenant = tenantOf("alice");
        UUID company = companies.verified(tenant, "fake");
        TestDocuments.Created doc = docs.pending(tenant, company, "t-" + UUID.randomUUID() + ":invoice",
                d -> d.nextAttemptAt = java.time.Instant.now().plusSeconds(3600));

        as("olga").post("/api/admin/v1/documents/" + doc.id() + "/hold").then().statusCode(200)
                .body("hold_reason", equalTo("operator"));
        // Retrying a held document clears the hold and attempts now
        as("olga").post("/api/admin/v1/documents/" + doc.id() + "/retry").then().statusCode(200)
                .body("hold_reason", org.hamcrest.Matchers.nullValue())
                .body("next_attempt_at", notNullValue());
        await().atMost(Duration.ofSeconds(20)).until(() -> "accepted".equals(docs.get(tenant, doc.id()).status));

        // The tenant sees what the platform did
        as("alice").get("/api/v1/tenants/" + tenant + "/audit?limit=200").then().statusCode(200)
                .body("items.action", hasItem("document.retried"));
        // A definitive document can't be retried
        as("olga").post("/api/admin/v1/documents/" + doc.id() + "/retry").then().statusCode(409);
        as("olga").post("/api/admin/v1/documents/" + UUID.randomUUID() + "/retry").then().statusCode(404);
    }

    @Test
    void releasing_a_held_document_makes_it_due() {
        UUID tenant = tenantOf("alice");
        UUID company = companies.verified(tenant, "fake");
        TestDocuments.Created doc = docs.pending(tenant, company, "t-" + UUID.randomUUID() + ":invoice", d -> {
            d.holdReason = "operator";
            d.nextAttemptAt = java.time.Instant.now().plusSeconds(3600);
        });
        as("olga").post("/api/admin/v1/documents/" + doc.id() + "/release").then().statusCode(200)
                .body("hold_reason", org.hamcrest.Matchers.nullValue());
        await().atMost(Duration.ofSeconds(20)).until(() -> "accepted".equals(docs.get(tenant, doc.id()).status));
    }

    @Test
    void endpoints_can_be_held_and_capped() {
        UUID tenant = tenantOf("alice");
        UUID company = companies.verified(tenant, "fake");
        String endpoint = "t-" + UUID.randomUUID() + ":invoice";
        docs.pending(tenant, company, endpoint, d -> d.nextAttemptAt = java.time.Instant.now().plusSeconds(3600));
        // The reconciler registers endpoints it sees in the backlog
        await().atMost(Duration.ofSeconds(10)).until(() -> as("olga").get("/api/admin/v1/endpoints").then().extract()
                .jsonPath().getList("id").contains(endpoint));

        as("olga").body(Map.of("concurrency_cap", 3)).put("/api/admin/v1/endpoints/" + endpoint).then().statusCode(200)
                .body("concurrency_cap", equalTo(3));
        as("olga").post("/api/admin/v1/endpoints/" + endpoint + "/hold").then().statusCode(200).body("paused", equalTo(true));
        as("olga").post("/api/admin/v1/endpoints/" + endpoint + "/release").then().statusCode(200).body("paused", equalTo(false));
        as("olga").post("/api/admin/v1/repair/requeue-stuck").then().statusCode(200).body("count", greaterThanOrEqualTo(0));
    }
}
