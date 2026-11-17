package io.github.projectopenubl.xhandler.ose;

import static io.github.projectopenubl.xhandler.Api.as;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.projectopenubl.xhandler.Api;
import io.github.projectopenubl.xhandler.engine.EngineStub;
import io.github.projectopenubl.xhandler.jobs.JobContext;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** P10: the platform as OSE — SOAP and REST intake, CDR-OSE, forwarding, refusals, validity. */
@QuarkusTest
@QuarkusTestResource(EngineStub.class)
class OseTest {

    @Inject
    OseJobs.Forwarder forwarder;

    @Inject
    OseJobs.Purge purge;

    @Inject
    io.github.projectopenubl.xhandler.storage.FileStore files;

    @Inject
    EntityManager em;

    String ruc;
    String username;
    String password;

    @BeforeEach
    void setUp() {
        ruc = Api.randomRuc();
        Map<String, Object> client = as("olga").body(Map.of("issuer_ruc", ruc)).post("/api/admin/v1/ose/clients").then().statusCode(200)
                .extract().as(Map.class);
        username = (String) client.get("username");
        password = (String) client.get("password");
        as("alice").body(Map.of("issuer_ruc", ruc)).post("/api/admin/v1/ose/clients").then().statusCode(403);
    }

    @AfterEach
    void tearDown() {
        EngineStub.reset();
    }

    void associate(String... rucs) {
        List<Map<String, Object>> entries = java.util.Arrays.stream(rucs).map(r -> Map.<String, Object>of("ruc", r, "starts_on", "2024-01-01"))
                .toList();
        as("olga").body(entries).put("/api/admin/v1/ose/association").then().statusCode(200);
    }

    static byte[] zip(Map<String, String> files) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> f : files.entrySet()) {
                out.putNextEntry(new ZipEntry(f.getKey()));
                out.write(f.getValue().getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    String xml(String serieNumero, boolean signed, String extra) {
        return "<Invoice><cbc:ID>" + serieNumero + "</cbc:ID><ruc>" + ruc + "</ruc>" + extra + (signed ? "<ds:Signature/>" : "") + "</Invoice>";
    }

    Response soap(String user, String pass, String operation, String body) {
        String envelope = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:ser=\"http://service.sunat.gob.pe\""
                + " xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\"><soapenv:Header><wsse:Security>"
                + "<wsse:UsernameToken><wsse:Username>" + user + "</wsse:Username><wsse:Password>" + pass + "</wsse:Password></wsse:UsernameToken>"
                + "</wsse:Security></soapenv:Header><soapenv:Body><ser:" + operation + ">" + body + "</ser:" + operation + "></soapenv:Body></soapenv:Envelope>";
        return given().contentType("text/xml").body(envelope).post("/ol-ti-itcpfegem/billService");
    }

    Response sendBill(String name, byte[] zip) {
        return soap(username, password, "sendBill",
                "<fileName>" + name + ".zip</fileName><contentFile>" + Base64.getEncoder().encodeToString(zip) + "</contentFile>");
    }

    static Map<String, String> unzip(String base64) throws Exception {
        Map<String, String> files = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(base64)))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                files.put(e.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return files;
    }

    static String between(String text, String open, String close) {
        int a = text.indexOf(open);
        return a < 0 ? null : text.substring(a + open.length(), text.indexOf(close, a));
    }

    @Test
    void documents_get_a_cdr_ose_and_are_forwarded_to_sunat() throws Exception {
        String name = ruc + "-01-F001-1";
        byte[] zip = zip(Map.of(name + ".xml", xml("F001-1", true, "")));

        // Wrong password, then an issuer that hasn't chosen us in SOL
        sendBill(name, zip).then().statusCode(500); // fault
        assertTrue(soap(username, "bad", "sendBill", "<fileName>x.zip</fileName><contentFile>AA==</contentFile>").asString()
                .contains("Client.0102"));
        assertTrue(sendBill(name, zip).asString().contains("Client.0154"));
        associate(ruc);

        // The CDR-OSE at once
        Response accepted = sendBill(name, zip);
        accepted.then().statusCode(200);
        Map<String, String> cdr = unzip(between(accepted.asString(), "<applicationResponse>", "</applicationResponse>"));
        assertTrue(cdr.get("R-" + name + ".xml").contains("ApplicationResponse"), cdr.toString());
        assertTrue(cdr.get("R-" + name + ".xml").contains("Signature"), "signed");
        // The same bytes again: the same answer; other bytes with the number: 1033
        sendBill(name, zip).then().statusCode(200);
        assertTrue(sendBill(name, zip(Map.of(name + ".xml", xml("F001-1", true, "<x/>")))).asString().contains("Client.1033"));
        // Unsigned, GRE, a name that doesn't match the content, a zip bomb-ish entry count
        assertTrue(sendBill(ruc + "-01-F001-2", zip(Map.of(ruc + "-01-F001-2.xml", xml("F001-2", false, "")))).asString().contains("Client.0306"));
        assertTrue(sendBill(ruc + "-09-T001-1", zip(Map.of(ruc + "-09-T001-1.xml", xml("T001-1", true, "")))).asString().contains("Client.1003"));
        assertTrue(sendBill(ruc + "-01-F001-3", zip(Map.of(ruc + "-01-F001-3.xml", xml("F001-9", true, "")))).asString().contains("Client.0151"));
        assertTrue(sendBill(ruc + "-01-F001-4", zip(Map.of(ruc + "-01-F001-4.xml", xml("F001-4", true, ""), "other.xml", "<x/>"))).asString()
                .contains("Client.0158"));

        // getStatusCdr
        String status = soap(username, password, "getStatusCdr", "<rucComprobante>" + ruc + "</rucComprobante><tipoComprobante>01</tipoComprobante>"
                + "<serieComprobante>F001</serieComprobante><numeroComprobante>1</numeroComprobante>").asString();
        assertTrue(status.contains("<statusCode>0004</statusCode>"), status);

        // Forwarded to SUNAT with its CDR, within the hour
        forwarder.run(new JobContext(UUID.randomUUID(), null, "ose.forward", 1, new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode(),
                null, () -> false));
        assertFalse(EngineStub.requests("/v1/ose/forward", name).isEmpty());
        String forward = QuarkusTransaction.requiringNew().call(() -> (String) em.createNativeQuery(
                        "SELECT forward_status FROM xhandler.ose_submission WHERE name = :n AND result = 'cdr'").setParameter("n", name).getSingleResult());
        assertEquals("forwarded", forward);

        // The public validity query: status only
        given().contentType("application/json").body(Map.of("ruc", ruc, "tipo_comprobante", "01", "serie", "F001", "numero", 1, "captcha", "test-pass"))
                .post("/api/v1/public/lookup/ose").then().statusCode(200).body("state", equalTo("con_cdr"));
        given().contentType("application/json").body(Map.of("ruc", ruc, "tipo_comprobante", "01", "serie", "F001", "numero", 2, "captcha", "test-pass"))
                .post("/api/v1/public/lookup/ose").then().statusCode(200).body("state", equalTo("con_inconsistencia"));

        // Blocked issuers are refused
        as("olga").body(List.of(Map.of("ruc", ruc))).put("/api/admin/v1/ose/blocked/dl1532").then().statusCode(200);
        assertTrue(sendBill(ruc + "-01-F001-5", zip(Map.of(ruc + "-01-F001-5.xml", xml("F001-5", true, "")))).asString().contains("Client.2010"));
        as("olga").body(List.of()).put("/api/admin/v1/ose/blocked/dl1532").then().statusCode(200);
        as("olga").get("/api/admin/v1/ose/forwarding").then().statusCode(200);
        as("olga").get("/api/admin/v1/ose/usage").then().statusCode(200);

        // The engine down: the issuer gets "service unavailable" and nothing is recorded or billed
        EngineStub.on("/v1/inspect", body -> new EngineStub.Reply(502, "{\"kind\":\"other\",\"message\":\"down\"}"));
        String down = ruc + "-01-F001-7";
        assertTrue(sendBill(down, zip(Map.of(down + ".xml", xml("F001-7", true, "")))).asString().contains("Client.0109"));
        EngineStub.reset();
        assertEquals(0L, (long) QuarkusTransaction.requiringNew().call(() -> ((Number) em.createNativeQuery(
                "SELECT count(*) FROM xhandler.ose_submission WHERE name = :n").setParameter("n", down).getSingleResult()).longValue()));
    }

    @Test
    void credential_faults_block_forwarding_instead_of_rejecting_and_the_purge_deletes_files() throws Exception {
        associate(ruc);
        String first = ruc + "-01-F005-1";
        String second = ruc + "-01-F005-2";
        sendBill(first, zip(Map.of(first + ".xml", xml("F005-1", true, "")))).then().statusCode(200);
        sendBill(second, zip(Map.of(second + ".xml", xml("F005-2", true, "")))).then().statusCode(200);
        // SUNAT refuses the OSE's own user: not the documents' fault
        EngineStub.on("/v1/ose/forward", body -> EngineStub.Reply.ok("{\"status\":\"blocked\",\"code\":\"0102\",\"description\":\"Usuario o contraseña incorrectos\"}"));
        forwarder.run(new JobContext(UUID.randomUUID(), null, "ose.forward", 1, new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode(),
                null, () -> false));
        List<String> states = QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                        "SELECT forward_status FROM xhandler.ose_submission WHERE name IN (:a, :b) AND result = 'cdr'")
                .setParameter("a", first).setParameter("b", second).getResultList());
        assertTrue(states.contains("blocked") && !states.contains("rejected") && !states.contains("forwarded"), states.toString());

        // Old forwarded submissions go, with their files
        EngineStub.reset();
        String xmlRef = QuarkusTransaction.requiringNew().call(() -> {
            em.createNativeQuery("UPDATE xhandler.ose_submission SET forward_status = 'forwarded', purge_after = now() - interval '1 day' WHERE name = :n")
                    .setParameter("n", first).executeUpdate();
            return (String) em.createNativeQuery("SELECT xml_ref FROM xhandler.ose_submission WHERE name = :n").setParameter("n", first).getSingleResult();
        });
        purge.run(new JobContext(UUID.randomUUID(), null, "ose.purge", 1, new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode(),
                null, () -> false));
        assertEquals(0L, (long) QuarkusTransaction.requiringNew().call(() -> ((Number) em.createNativeQuery(
                "SELECT count(*) FROM xhandler.ose_submission WHERE name = :n").setParameter("n", first).getSingleResult()).longValue()));
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> files.get(xmlRef));
    }

    @Test
    void packs_summaries_rest_and_lockout() throws Exception {
        associate(ruc);
        // A pack: one good document, one whose name doesn't match its content
        byte[] pack = zip(Map.of(ruc + "-01-F002-1.xml", xml("F002-1", true, ""), ruc + "-01-F002-2.xml", xml("F002-9", true, "")));
        String ticket = between(soap(username, password, "sendPack", "<fileName>" + ruc + "-PACK.zip</fileName><contentFile>"
                + Base64.getEncoder().encodeToString(pack) + "</contentFile>").asString(), "<ticket>", "</ticket>");
        String status = soap(username, password, "getStatus", "<ticket>" + ticket + "</ticket>").asString();
        assertTrue(status.contains("<statusCode>99</statusCode>"), status);
        Map<String, String> content = unzip(between(status, "<content>", "</content>"));
        assertTrue(content.containsKey("R-" + ruc + "-01-F002-1.xml") && content.get("errores.txt").contains("0151"), content.keySet().toString());
        assertTrue(soap(username, password, "getStatus", "<ticket>999</ticket>").asString().contains("Client.0127"));

        // A resumen diario through the ticket
        String summary = ruc + "-RC-20270115-1";
        String rc = "<SummaryDocuments><cbc:ID>RC-20270115-1</cbc:ID><ruc>" + ruc + "</ruc><ds:Signature/></SummaryDocuments>";
        String summaryTicket = between(soap(username, password, "sendSummary", "<fileName>" + summary + ".zip</fileName><contentFile>"
                + Base64.getEncoder().encodeToString(zip(Map.of(summary + ".xml", rc))) + "</contentFile>").asString(), "<ticket>", "</ticket>");
        assertTrue(soap(username, password, "getStatus", "<ticket>" + summaryTicket + "</ticket>").asString().contains("<statusCode>0</statusCode>"));

        // REST with Basic auth
        String basic = "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        String name = ruc + "-01-F003-1";
        given().header("Authorization", basic).contentType("application/json")
                .body(Map.of("file_name", name + ".zip", "content_base64", Base64.getEncoder().encodeToString(zip(Map.of(name + ".xml", xml("F003-1", true, ""))))))
                .post("/api/v1/ose/documents").then().statusCode(200).body("result", equalTo("cdr"));
        given().header("Authorization", basic).get("/api/v1/ose/documents/" + name + "/cdr").then().statusCode(200);
        given().header("Authorization", basic).contentType("application/json")
                .body(Map.of("file_name", ruc + "-01-F003-2.zip", "content_base64", Base64.getEncoder().encodeToString(
                        zip(Map.of(ruc + "-01-F003-2.xml", xml("F003-2", false, ""))))))
                .post("/api/v1/ose/documents").then().statusCode(422).body("kind", equalTo("inconsistency")).body("detail", equalTo("0306"));

        // Wrong passwords never lock the issuer out (anyone knowing the username could): the right one still works
        for (int i = 0; i < 6; i++) {
            given().header("Authorization", "Basic " + Base64.getEncoder().encodeToString((username + ":bad").getBytes(StandardCharsets.UTF_8)))
                    .get("/api/v1/ose/documents/" + name + "/cdr").then().statusCode(401);
        }
        given().header("Authorization", basic).get("/api/v1/ose/documents/" + name + "/cdr").then().statusCode(200);

        // An identical resend through a ticket is answered by that ticket too
        String again = between(soap(username, password, "sendSummary", "<fileName>" + summary + ".zip</fileName><contentFile>"
                + Base64.getEncoder().encodeToString(zip(Map.of(summary + ".xml", rc))) + "</contentFile>").asString(), "<ticket>", "</ticket>");
        assertTrue(soap(username, password, "getStatus", "<ticket>" + again + "</ticket>").asString().contains("<statusCode>0</statusCode>"));
        // A pack entry with a bad name gets its own answer; the others still go through
        byte[] mixed = zip(Map.of("bad-name.xml", "<x/>", ruc + "-01-F004-1.xml", xml("F004-1", true, "")));
        String mixedTicket = between(soap(username, password, "sendPack", "<fileName>" + ruc + "-PACK2.zip</fileName><contentFile>"
                + Base64.getEncoder().encodeToString(mixed) + "</contentFile>").asString(), "<ticket>", "</ticket>");
        Map<String, String> mixedContent = unzip(between(soap(username, password, "getStatus", "<ticket>" + mixedTicket + "</ticket>").asString(),
                "<content>", "</content>"));
        assertTrue(mixedContent.containsKey("R-" + ruc + "-01-F004-1.xml") && mixedContent.get("errores.txt").contains("bad-name"),
                mixedContent.toString());
        // A DOCTYPE in the envelope is refused
        given().contentType("text/xml").body("<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><x>&e;</x>")
                .post("/ose/billService").then().statusCode(500).body(containsString("Client.0306"));
    }
}
