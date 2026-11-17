package io.github.projectopenubl.xhandler.reports;

import static io.github.projectopenubl.xhandler.Api.as;
import static io.github.projectopenubl.xhandler.Api.tenantOf;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.projectopenubl.xhandler.company.TestCompanies;
import io.github.projectopenubl.xhandler.conformity.ConformityTracker;
import io.github.projectopenubl.xhandler.documents.Deadlines;
import io.github.projectopenubl.xhandler.engine.EngineStub;
import io.github.projectopenubl.xhandler.sire.SireService;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** P9: registers as reports, SIRE round trip, factura negociable conformity. */
@QuarkusTest
@QuarkusTestResource(EngineStub.class)
class TaxComplianceTest {

    @Inject
    TestCompanies companies;

    @Inject
    SireService sire;

    @Inject
    ConformityTracker conformity;

    @Inject
    EntityManager em;

    UUID tenant;
    UUID company;
    String ruc;
    String base;
    String period;

    @BeforeEach
    void setUp() {
        tenant = tenantOf("alice");
        company = companies.verified(tenant, "fake");
        base = "/api/v1/tenants/" + tenant + "/companies/" + company;
        ruc = as("alice").get(base).then().extract().path("ruc");
        period = Deadlines.todayInLima().format(DateTimeFormatter.ofPattern("yyyyMM"));
    }

    @AfterEach
    void tearDown() {
        EngineStub.reset();
    }

    String emit(Map<String, Object> spec) {
        String id = as("alice").body(Map.of("document", Map.of("kind", "Invoice", "spec", spec))).post(base + "/documents").then()
                .statusCode(201).extract().path("id");
        await().atMost(Duration.ofSeconds(20)).until(() -> "accepted".equals(
                as("alice").get("/api/v1/tenants/" + tenant + "/documents/" + id).then().extract().path("status")));
        return id;
    }

    static Map<String, Object> cliente() {
        return Map.of("tipo_documento_identidad", "6", "numero_documento_identidad", "20111111111", "nombre", "CLIENTE SAC");
    }

    String serieNumero(String documentId) {
        return as("alice").get("/api/v1/tenants/" + tenant + "/documents/" + documentId).then().extract().path("serie_numero");
    }

    Map<String, Object> report(String register, String format) {
        String id = as("alice").body(Map.of("register", register, "format", format, "period", period)).post(base + "/reports").then()
                .statusCode(202).body("status", equalTo("pending")).extract().path("id");
        await().atMost(Duration.ofSeconds(20)).until(() -> "done".equals(as("alice").get(base + "/reports/" + id).then().extract().path("status")));
        return as("alice").get(base + "/reports/" + id).then().extract().as(Map.class);
    }

    byte[] file(Object reportId) {
        return as("alice").get(base + "/reports/" + reportId + "/file").then().statusCode(200).extract().asByteArray();
    }

    @Test
    void registers_come_out_as_csv_excel_ple_and_sire() throws Exception {
        String factura = serieNumero(emit(Map.of("cliente", cliente(), "detalles", List.of())));

        Map<String, Object> csv = report("sales", "csv");
        assertEquals(1, csv.get("rows"));
        String text = new String(file(csv.get("id")), StandardCharsets.UTF_8);
        assertTrue(text.contains(factura.split("-")[0]) && text.contains("1000.00") && text.contains("180.00") && text.contains("1180.00"), text);

        byte[] xlsx = file(report("sales", "xlsx").get("id"));
        assertTrue(xlsx[0] == 'P' && xlsx[1] == 'K', "an xlsx is a zip");

        Map<String, Object> ple = report("sales", "ple");
        assertTrue(((String) ple.get("file_name")).startsWith("LE" + ruc + period + "00140100"), String.valueOf(ple.get("file_name")));
        String pleText = new String(file(ple.get("id")), StandardCharsets.UTF_8);
        assertTrue(pleText.startsWith(period + "00|") && pleText.trim().endsWith("|1|"), pleText);

        byte[] sireZip = file(report("sales", "sire").get("id"));
        assertTrue(RegisterFormats.unzipFirst(sireZip).contains("|" + factura.split("-")[0] + "|"));

        // Purchases come from receptions (none yet) and another tenant sees nothing
        assertEquals(0, report("purchases", "csv").get("rows"));
        as("bob").get(base + "/reports").then().statusCode(404);
    }

    @Test
    void sire_proposal_is_reconciled_and_replaced() {
        String factura = serieNumero(emit(Map.of("cliente", cliente(), "detalles", List.of())));
        String boleta = serieNumero(emit(Map.of("tipo_comprobante", "03", "cliente", cliente(), "detalles", List.of())));
        String sireBase = base + "/sire/rvie/" + period;

        // Without the API credentials the period waits for them
        String id = as("alice").post(sireBase + "/proposal").then().statusCode(202).body("status", equalTo("requested")).extract().path("id");
        await().atMost(Duration.ofSeconds(20)).until(() -> "credentials".equals(as("alice").get(sireBase).then().extract().path("status")));

        // Saving them resumes the period by itself
        as("alice").body(Map.of("username", ruc + "SIRE", "password", "x", "client_id", "id", "client_secret", "secret"))
                .put(base + "/credentials/sire").then().statusCode(200);
        as("alice").get(sireBase).then().body("status", equalTo("requested"));
        sire.poll(tenant, UUID.fromString(id));
        await().atMost(Duration.ofSeconds(20)).until(() -> EngineStub.requests().stream().anyMatch(r -> r.path().equals("/v1/sire/proposal")));

        // SUNAT's proposal: the factura (same total), not the boleta, plus one it has that isn't stored
        String[] f = factura.split("-");
        String row = ruc + "|EMPRESA|" + period + "||01/01/2027||01|" + f[0] + "|" + f[1]
                + "||6|20111111111|CLIENTE SAC|0.00|1000.00|0.00|180.00|0.00|0.00|0.00|0.00|0.00|0.00|0.00|0.00|1180.00|PEN|||||";
        String extra = row.replace("|" + f[0] + "|" + f[1] + "|", "|F999|1|");
        String txt = RegisterFormats.RVIE_HEADER + "\n" + row + "\n" + extra;
        String zip = Base64.getEncoder().encodeToString(RegisterFormats.zip(Map.of("LE.txt", txt)));
        EngineStub.on("/v1/sire/ticket", body -> body.contains("PROPOSAL-1")
                ? EngineStub.Reply.ok("{\"state\":\"ok\",\"ticket_state\":\"done\",\"description\":\"Terminado\",\"files\":[{\"name\":\"LE.zip\",\"content_base64\":\""
                        + zip + "\"}]}")
                : EngineStub.Reply.ok("{\"state\":\"ok\",\"ticket_state\":\"done\",\"description\":\"Terminado\",\"files\":[]}"));
        await().atMost(Duration.ofSeconds(20)).until(() -> {
            sire.poll(tenant, UUID.fromString(id));
            return "proposal_ready".equals(as("alice").get(sireBase).then().extract().path("status"));
        });
        as("alice").get(sireBase).then().body("reconciliation.matches", equalTo(false))
                .body("reconciliation.missing_in_sunat", hasItem("03-" + boleta))
                .body("reconciliation.missing_locally", hasItem("01-F999-1"))
                .body("reconciliation.sunat_count", equalTo(2)).body("proposal_file", equalTo(true));

        // The stored rows don't match SUNAT's proposal: submitting them needs an explicit acknowledgement
        as("alice").contentType("application/json").body(Map.of()).post(sireBase + "/replace").then().statusCode(409)
                .body("kind", equalTo("sire_differences"));
        // The answer to the replacement is lost: unknown, and nothing is resent without confirmation
        EngineStub.on("/v1/sire/replace", body -> EngineStub.Reply.ok("{\"state\":\"unknown\",\"ticket\":null,\"description\":\"timeout\"}"));
        as("alice").body(Map.of("acknowledge_differences", true)).post(sireBase + "/replace").then().statusCode(202)
                .body("status", equalTo("submitting"));
        await().atMost(Duration.ofSeconds(20)).until(() -> "unknown".equals(as("alice").get(sireBase).then().extract().path("status")));
        as("alice").body(Map.of("acknowledge_differences", true)).post(sireBase + "/replace").then().statusCode(409)
                .body("kind", equalTo("sire_unknown"));
        as("alice").post(sireBase + "/proposal").then().statusCode(409).body("kind", equalTo("sire_unknown"));

        // Checked in SOL that SUNAT didn't get it: an admin sends it again and SUNAT accepts it
        EngineStub.on("/v1/sire/replace", body -> EngineStub.Reply.ok("{\"state\":\"ok\",\"ticket\":\"REPLACE-1\",\"description\":null}"));
        as("alice").body(Map.of("acknowledge_differences", true, "confirm_not_received", true)).post(sireBase + "/replace").then()
                .statusCode(202).body("status", equalTo("submitting"));
        await().atMost(Duration.ofSeconds(20)).until(() -> "submitted".equals(as("alice").get(sireBase).then().extract().path("status")));
        String replacement = EngineStub.lastBody("/v1/sire/replace");
        assertTrue(replacement.contains("\"book\":\"rvie\"") && replacement.contains("\"period\":\"" + period + "\""), replacement);
        sire.poll(tenant, UUID.fromString(id));
        as("alice").get(sireBase).then().body("status", equalTo("accepted")).body("replacement_file", equalTo(true));
        as("alice").get("/api/v1/tenants/" + tenant + "/notifications").then().body("items.kind", hasItem("sire.accepted"));
    }

    @Test
    void credit_facturas_track_conformity_until_the_buyer_answers() {
        String today = Deadlines.todayInLima().toString();
        String credit = emit(Map.of("cliente", cliente(), "detalles", List.of(),
                "forma_pago", Map.of("cuotas", List.of(Map.of("importe", 1180, "fecha_pago", Deadlines.todayInLima().plusDays(30).toString())))));
        String cash = emit(Map.of("cliente", cliente(), "detalles", List.of(), "fecha_emision", today));
        String docs = "/api/v1/tenants/" + tenant + "/documents/";
        as("alice").get(docs + credit + "/conformity").then().statusCode(200).body("state", equalTo("pending"));
        as("alice").get(docs + cash + "/conformity").then().statusCode(404);

        UUID conformityId = (UUID) io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().call(() -> {
            em.createNativeQuery("SELECT set_config('app.tenant_id', :t, true)").setParameter("t", tenant.toString()).getSingleResult();
            return em.createNativeQuery("SELECT id FROM xhandler.conformity WHERE document_id = :d").setParameter("d", UUID.fromString(credit))
                    .getSingleResult();
        });
        // Without the API credentials nothing is asked; with them, SUNAT says pending, then objected
        conformity.check(tenant, conformityId);
        as("alice").get(docs + credit + "/conformity").then().body("state", equalTo("pending"))
                .body("check_detail", org.hamcrest.Matchers.containsString("credenciales"));
        as("alice").body(Map.of("username", ruc + "SIRE", "password", "x", "client_id", "id", "client_secret", "secret"))
                .put(base + "/credentials/sire").then().statusCode(200);
        conformity.check(tenant, conformityId);
        as("alice").get(docs + credit + "/conformity").then().body("state", equalTo("pending"));
        EngineStub.on("/v1/conformity", body -> EngineStub.Reply.ok("{\"state\":\"ok\",\"conformity\":\"objected\",\"description\":\"Disconforme\"}"));
        conformity.check(tenant, conformityId);
        as("alice").get(docs + credit + "/conformity").then().body("state", equalTo("objected"))
                .body("suggested_credit_note.kind", equalTo("CreditNote")).body("suggested_credit_note.spec.tipo_nota", equalTo("13"));
        as("alice").get("/api/v1/tenants/" + tenant + "/notifications").then().body("items.kind", hasItem("conformity.objected"));
        String export = as("alice").get(base + "/conformity/export?state=objected").then().statusCode(200).extract().asString();
        assertTrue(export.contains(serieNumero(credit)) && export.contains("objected"), export);
        as("alice").get(base + "/conformity?state=bad").then().statusCode(422);
    }
}
