package io.github.projectopenubl.xhandler.documents;

import static io.github.projectopenubl.xhandler.Api.as;
import static io.github.projectopenubl.xhandler.Api.tenantOf;
import static io.github.projectopenubl.xhandler.engine.EngineStub.Reply;
import static io.github.projectopenubl.xhandler.engine.EngineStub.sendAnswer;
import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.projectopenubl.xhandler.company.TestCompanies;
import io.github.projectopenubl.xhandler.engine.EngineStub;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** P5: PDFs, email, notifications, catalogs, RUC/DNI lookup, public lookup by data. */
@QuarkusTest
@QuarkusTestResource(EngineStub.class)
class DeliveryTest {

    static final Duration WAIT = Duration.ofSeconds(20);

    @Inject
    TestCompanies companies;

    @Inject
    TestDocuments docs;

    @Inject
    MockMailbox mailbox;

    UUID tenant;
    UUID company;
    String base;

    @BeforeEach
    void setUp() {
        tenant = tenantOf("alice");
        company = companies.verified(tenant, "fake");
        base = "/api/v1/tenants/" + tenant;
        mailbox.clear();
    }

    @AfterEach
    void tearDown() {
        EngineStub.reset();
    }

    Map<String, Object> invoice(Map<String, Object> cliente) {
        Map<String, Object> spec = new HashMap<>(Map.of("cliente", cliente,
                "detalles", List.of(Map.of("descripcion", "Servicio de soporte", "cantidad", 2, "precio", 50))));
        return Map.of("document", Map.of("kind", "Invoice", "spec", spec), "delivered", false);
    }

    Map<String, Object> cliente() {
        return Map.of("tipo_documento_identidad", "6", "numero_documento_identidad", "20111111111", "nombre", "Comprador SAC",
                "contacto", Map.of("email", "compras@comprador.pe"));
    }

    Response emit(Map<String, Object> body) {
        return as("alice").body(body).post(base + "/companies/" + company + "/documents");
    }

    String text(byte[] pdf) throws Exception {
        try (var document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    @Test
    void pdfs_show_the_document_and_its_status() throws Exception {
        String id = emit(invoice(cliente())).then().statusCode(201).extract().path("id");
        await().atMost(WAIT).until(() -> "accepted".equals(as("alice").get(base + "/documents/" + id).then().extract().path("status")));
        byte[] pdf = as("alice").get(base + "/documents/" + id + "/files/pdf").then().statusCode(200).contentType("application/pdf")
                .extract().asByteArray();
        String text = text(pdf);
        assertTrue(text.contains("FACTURA ELECTRÓNICA"), text);
        assertTrue(text.contains("F001-1"), text);
        assertTrue(text.contains("Comprador SAC"), text);
        assertTrue(text.contains("SIN VALOR LEGAL"), "a sandbox document says it has no legal value");

        // A ticket company, and a document still waiting for SUNAT
        as("alice").body(Map.of("pdf_format", "ticket80")).put(base + "/companies/" + company + "/settings").then().statusCode(200);
        EngineStub.on("/v1/send", body -> body.contains("F001-2") ? Reply.ok(sendAnswer("not_delivered", null, null, null))
                : Reply.ok(sendAnswer("accepted", 0, null, null)));
        Response pending = emit(invoice(cliente()));
        String pendingText = text(given().get(apiPath(pending.<String>path("access_url")) + "/pdf").then().statusCode(200)
                .extract().asByteArray());
        assertTrue(pendingText.contains("SIN VALOR LEGAL"), pendingText);
        // Production documents carry their status instead
        assertTrue(io.github.projectopenubl.xhandler.pdf.DocumentPdfAccess.banner("pending_send").startsWith("PENDIENTE"));
        assertEquals(null, io.github.projectopenubl.xhandler.pdf.DocumentPdfAccess.banner("accepted"));
    }

    @Test
    void documents_are_emailed_with_pdf_and_xml_and_the_delivery_is_logged() {
        String id = emit(invoice(cliente())).then().statusCode(201).extract().path("id");
        await().atMost(WAIT).until(() -> "accepted".equals(as("alice").get(base + "/documents/" + id).then().extract().path("status")));
        as("alice").body(Map.of("to", List.of("compras@comprador.pe"), "include_cdr", true))
                .post(base + "/documents/" + id + "/email").then().statusCode(202);
        await().atMost(WAIT).until(() -> !mailbox.getMailsSentTo("compras@comprador.pe").isEmpty());
        var mail = mailbox.getMailsSentTo("compras@comprador.pe").get(0);
        assertTrue(mail.getSubject().contains("F001-1"), mail.getSubject());
        assertEquals(3, mail.getAttachments().size(), "PDF, XML and CDR");
        assertTrue(mail.getHtml().contains("/p/documentos/"), "with the buyer's link");
        await().atMost(WAIT).until(() -> "service".equals(as("alice").get(base + "/documents/" + id).then().extract().path("delivered_source")));

        as("alice").body(Map.of("to", List.of("not-an-email"))).post(base + "/documents/" + id + "/email").then().statusCode(422);
    }

    @Test
    void companies_can_email_buyers_when_sunat_accepts_production_documents() throws Exception {
        // Sandbox documents are never emailed automatically
        as("alice").body(Map.of("email_on_accept", true)).put(base + "/companies/" + company + "/settings").then().statusCode(200);
        String sandbox = emit(invoice(cliente())).then().statusCode(201).extract().path("id");
        await().atMost(WAIT).until(() -> "accepted".equals(as("alice").get(base + "/documents/" + sandbox).then().extract().path("status")));
        Thread.sleep(1000);
        assertTrue(mailbox.getMailsSentTo("compras@comprador.pe").isEmpty());

        String ruc = io.github.projectopenubl.xhandler.Api.randomRuc();
        UUID production = companies.verified(tenant, "production", ruc);
        java.io.File pfx = io.github.projectopenubl.xhandler.keys.TestCertificates.generate(ruc);
        io.restassured.RestAssured.given().auth().oauth2(new io.quarkus.test.keycloak.client.KeycloakTestClient().getAccessToken("alice"))
                .multiPart("file", pfx, "application/x-pkcs12").multiPart("password", io.github.projectopenubl.xhandler.keys.TestCertificates.PASSWORD)
                .put(base + "/companies/" + production + "/certificate").then().statusCode(200);
        as("alice").body(Map.of("username", ruc + "USER", "password", "x")).put(base + "/companies/" + production + "/credentials/sol").then().statusCode(200);
        as("alice").body(Map.of("email_on_accept", true)).put(base + "/companies/" + production + "/settings").then().statusCode(200);
        as("alice").body(invoice(cliente())).post(base + "/companies/" + production + "/documents").then().statusCode(201);
        await().atMost(WAIT).until(() -> !mailbox.getMailsSentTo("compras@comprador.pe").isEmpty());
    }

    @Test
    void suppressed_addresses_in_cc_are_dropped() {
        String id = emit(invoice(cliente())).then().statusCode(201).extract().path("id");
        String bounced = "rebote-" + UUID.randomUUID() + "@comprador.pe";
        as("olga").body(Map.of("address", bounced)).post("/api/admin/v1/tenants/" + tenant + "/email-suppressions").then().statusCode(200);
        as("alice").body(Map.of("to", List.of("compras@comprador.pe"), "cc", List.of(bounced))).post(base + "/documents/" + id + "/email")
                .then().statusCode(202);
        await().atMost(WAIT).until(() -> !mailbox.getMailsSentTo("compras@comprador.pe").isEmpty());
        assertTrue(mailbox.getMailsSentTo(bounced).isEmpty());
    }

    @Test
    void a_lookup_keeps_what_the_user_entered() {
        String path = base + "/companies/" + company;
        as("alice").body(Map.of("tipo_documento_identidad", "6", "numero_documento_identidad", "20444444444", "nombre", "A mano",
                "email", "contacto@cliente.pe")).post(path + "/catalogs/customer").then().statusCode(201);
        as("alice").body(Map.of("url", EngineStub.BASE_URL.get() + "/lookup/{tipo}/{numero}")).put(path + "/credentials/lookup").then().statusCode(200);
        as("alice").get(path + "/lookup/ruc/20444444444").then().statusCode(200);
        as("alice").get(path + "/catalogs/customer?q=20444444444").then().statusCode(200)
                .body("items[0].data.email", equalTo("contacto@cliente.pe"))
                .body("items[0].data.nombre", equalTo("EMPRESA 20444444444 S.A.C."));
    }

    @Test
    void suppressed_addresses_are_not_emailed() {
        String id = emit(invoice(cliente())).then().statusCode(201).extract().path("id");
        String address = "rebote-" + UUID.randomUUID() + "@comprador.pe";
        as("olga").body(Map.of("address", address, "reason", "hard_bounce"))
                .post("/api/admin/v1/tenants/" + tenant + "/email-suppressions").then().statusCode(200);
        as("alice").body(Map.of("to", List.of(address))).post(base + "/documents/" + id + "/email").then().statusCode(422)
                .body("kind", equalTo("email_suppressed"));
        as("alice").get(base + "/email-suppressions").then().statusCode(200).body("address", hasItem(address));
        as("alice").delete(base + "/email-suppressions/" + address).then().statusCode(204);
    }

    @Test
    void tenants_are_notified_in_app_and_by_email() {
        String notify = "avisos-" + UUID.randomUUID() + "@empresa.pe";
        as("alice").body(Map.of("notification_email", notify)).put(base + "/notification-settings").then().statusCode(200);
        EngineStub.on("/v1/send", body -> Reply.ok(sendAnswer("exception", 1034, "permanent_content", null)));
        String id = emit(invoice(cliente())).then().statusCode(201).extract().path("id");
        await().atMost(WAIT).until(() -> as("alice").get(base + "/notifications?unread=true").then().extract()
                .jsonPath().getList("items.document_id").contains(id));
        await().atMost(WAIT).until(() -> !mailbox.getMailsSentTo(notify).isEmpty());

        // Email off for that kind: only in-app
        as("alice").body(List.of(Map.of("kind", "document.exception", "email", false, "in_app", true)))
                .put(base + "/notification-preferences").then().statusCode(200);
        as("alice").post(base + "/notifications/read-all").then().statusCode(204);
        as("alice").get(base + "/notifications?unread=true").then().statusCode(200).body("items", hasSize(0));
        as("alice").body(Map.of("notification_email", "")).put(base + "/notification-settings").then().statusCode(200);
        as("alice").body(List.of(Map.of("kind", "document.exception", "email", true, "in_app", true)))
                .put(base + "/notification-preferences").then().statusCode(200);
    }

    @Test
    void catalogs_store_customers_and_products() {
        String path = base + "/companies/" + company + "/catalogs";
        String id = as("alice").body(Map.of("tipo_documento_identidad", "6", "numero_documento_identidad", "20222222222", "nombre", "Cliente Uno"))
                .post(path + "/customer").then().statusCode(201).body("key", equalTo("6:20222222222")).extract().path("id");
        as("alice").body(Map.of("tipo_documento_identidad", "6", "numero_documento_identidad", "20222222222", "nombre", "Otro"))
                .post(path + "/customer").then().statusCode(409);
        as("alice").body(Map.of("codigo", "P-1", "descripcion", "Producto", "precio", 10)).post(path + "/product").then().statusCode(201);
        as("alice").body(Map.of("descripcion", "sin código")).post(path + "/product").then().statusCode(422);
        as("alice").get(path + "/customer?q=Uno").then().statusCode(200).body("items", hasSize(1));
        as("alice").body(Map.of("tipo_documento_identidad", "6", "numero_documento_identidad", "20222222222", "nombre", "Cliente Uno SAC"))
                .put(path + "/customer/" + id).then().statusCode(200).body("data.nombre", equalTo("Cliente Uno SAC"));
        as("alice").delete(path + "/customer/" + id).then().statusCode(204);
        as("alice").get(path + "/customer/" + id).then().statusCode(404);
        as("alice").get(path + "/spaceships").then().statusCode(404);
    }

    @Test
    void ruc_lookup_uses_the_companys_provider_and_caches_the_answer() {
        as("alice").body(Map.of("url", EngineStub.BASE_URL.get() + "/lookup/{tipo}/{numero}", "token", "tok"))
                .put(base + "/companies/" + company + "/credentials/lookup").then().statusCode(200);
        String path = base + "/companies/" + company + "/lookup/ruc/20333333333";
        as("alice").get(path).then().statusCode(200).body("nombre", equalTo("EMPRESA 20333333333 S.A.C.")).body("cached", equalTo(false));
        as("alice").get(path).then().statusCode(200).body("cached", equalTo(true));
        as("alice").get(base + "/companies/" + company + "/catalogs/customer?q=20333333333").then().body("items", hasSize(1));
        assertTrue(EngineStub.requests().stream().anyMatch(r -> r.body().equals("Bearer tok")), "the tenant's token is sent");
        as("alice").get(base + "/companies/" + company + "/lookup/dni/123").then().statusCode(422);
    }

    @Test
    void the_public_lookup_needs_a_captcha_and_answers_only_the_status() {
        UUID production = companies.verified(tenant, "production");
        String ruc = as("alice").get(base + "/companies/" + production).then().extract().path("ruc");
        TestDocuments.Created doc = docs.pending(tenant, production, "t-" + UUID.randomUUID() + ":invoice", d -> {
            d.sunatEnv = "production";
            d.status = "accepted";
            d.nextAttemptAt = null;
            d.total = new BigDecimal("118.00");
        });
        Document stored = docs.get(tenant, doc.id());
        Map<String, Object> request = new HashMap<>(Map.of("ruc", ruc, "tipo_comprobante", "01", "serie", "F001", "numero", stored.numero,
                "fecha_emision", stored.fechaEmision.toString(), "total", "118.00", "captcha", "wrong"));
        given().contentType("application/json").body(request).post("/api/v1/public/lookup").then().statusCode(403);
        request.put("captcha", "test-pass");
        given().contentType("application/json").body(request).post("/api/v1/public/lookup").then().statusCode(200)
                .body("status", equalTo("aceptado"))
                .body("$", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasKey("files")));
        request.put("total", "119.00");
        given().contentType("application/json").body(request).post("/api/v1/public/lookup").then().statusCode(200)
                .body("status", equalTo("no_encontrado"));
    }

    @Test
    void sender_domains_need_their_dns_record() {
        String domain = "d" + UUID.randomUUID().toString().substring(0, 8) + ".invalid";
        as("alice").body(Map.of("domain", domain)).post(base + "/sender-domains").then().statusCode(201)
                .body("txt_name", equalTo("_xhandler." + domain))
                .body("txt_value", org.hamcrest.Matchers.startsWith("xhandler-verification="));
        as("alice").post(base + "/sender-domains/" + domain + "/verify").then().statusCode(409).body("kind", equalTo("dns_record_missing"));
    }

    /** The public API behind a buyer's link (the link is the UI page /p/documentos/{token}). */
    static String apiPath(String accessUrl) {
        String link = URI.create(accessUrl).getPath();
        return "/api/v1/public/documents/" + link.substring(link.lastIndexOf('/') + 1);
    }
}
