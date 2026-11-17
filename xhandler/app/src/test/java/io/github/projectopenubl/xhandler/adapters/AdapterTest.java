package io.github.projectopenubl.xhandler.adapters;

import static io.github.projectopenubl.xhandler.Api.as;
import static io.github.projectopenubl.xhandler.Api.tenantOf;
import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;

import io.github.projectopenubl.xhandler.company.TestCompanies;
import io.github.projectopenubl.xhandler.engine.EngineStub;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** P8: UBL upload, SFS, the Nubefact-compatible API and supplier reception. */
@QuarkusTest
@QuarkusTestResource(EngineStub.class)
class AdapterTest {

    @Inject
    TestCompanies companies;

    UUID tenant;
    UUID company;
    String ruc;
    String base;

    @BeforeEach
    void setUp() {
        tenant = tenantOf("alice");
        company = companies.verified(tenant, "fake");
        base = "/api/v1/tenants/" + tenant + "/companies/" + company;
        ruc = as("alice").get(base).then().extract().path("ruc");
    }

    @AfterEach
    void tearDown() {
        EngineStub.reset();
    }

    String status(String id) {
        return as("alice").get("/api/v1/tenants/" + tenant + "/documents/" + id).then().extract().path("status");
    }

    @Test
    void a_ubl_xml_keeps_its_number_and_moves_the_series_past_it() {
        String xml = "<Invoice><cbc:ID>F777-5</cbc:ID><ruc>" + ruc + "</ruc><cliente>6|20111111111</cliente></Invoice>";
        String id = as("alice").contentType("application/xml").body(xml).post(base + "/documents/ubl").then().statusCode(201)
                .body("serie_numero", equalTo("F777-5")).extract().path("id");
        await().atMost(Duration.ofSeconds(20)).until(() -> "accepted".equals(status(id)));
        // The service's own numbering in that series continues after it
        as("alice").body(Map.of("serie", "F777", "document", Map.of("kind", "Invoice", "spec", Map.of(
                        "cliente", Map.of("tipo_documento_identidad", "6", "numero_documento_identidad", "20111111111", "nombre", "X"),
                        "detalles", List.of()))))
                .post(base + "/documents").then().statusCode(201).body("serie_numero", equalTo("F777-6"));
        // Someone else's XML, or the same number again
        as("alice").contentType("application/xml").body(xml.replace(ruc, "20999999999")).post(base + "/documents/ubl").then().statusCode(422);
        as("alice").contentType("application/xml").body(xml).post(base + "/documents/ubl").then().statusCode(409);
    }

    @Test
    void sfs_files_are_emitted() {
        String cab = "0101|" + io.github.projectopenubl.xhandler.documents.Deadlines.todayInLima()
                + "|10:30:00|-|0000|6|20111111111|CLIENTE SAC|PEN|18.00|100.00|118.00|0.00|0.00|0.00|118.00|2.1|2.0";
        String det = "NIU|1|P001|-|PRODUCTO|100|18.00|1000|18.00|100.00|IGV|VAT|10|18.00";
        as("alice").body(Map.of("file_name", ruc + "-01-F778-10", "cab", cab, "det", det)).post(base + "/documents/sfs").then().statusCode(201)
                .body("serie_numero", equalTo("F778-10"));
        as("alice").body(Map.of("file_name", "20999999999-01-F778-11", "cab", cab, "det", det)).post(base + "/documents/sfs").then()
                .statusCode(422);
    }

    @Test
    void nubefact_clients_switch_by_changing_url_and_token() {
        String token = as("alice").body(Map.of("label", "ERP antiguo")).post(base + "/adapter-tokens/nubefact").then().statusCode(201)
                .extract().path("token");
        String url = "/api/v1/public/nubefact/" + company;
        Map<String, Object> factura = Map.of("operacion", "generar_comprobante", "tipo_de_comprobante", 1, "serie", "F779",
                "numero", 3, "cliente_tipo_de_documento", "6", "cliente_numero_de_documento", "20111111111",
                "cliente_denominacion", "CLIENTE SAC", "moneda", 1,
                "items", List.of(Map.of("unidad_de_medida", "NIU", "descripcion", "Producto", "cantidad", 1, "valor_unitario", 100,
                        "tipo_de_igv", 1)));
        given().contentType("application/json").header("Authorization", "Token token=\"bad\"").body(factura).post(url).then().statusCode(401);
        given().contentType("application/json").header("Authorization", "Token token=\"" + token + "\"").body(factura).post(url).then()
                .statusCode(200).body("serie", equalTo("F779")).body("numero", equalTo(3)).body("aceptada_por_sunat", equalTo(false));
        Map<String, Object> consulta = Map.of("operacion", "consultar_comprobante", "tipo_de_comprobante", 1, "serie", "F779", "numero", 3);
        await().atMost(Duration.ofSeconds(20)).until(() -> Boolean.TRUE.equals(given().contentType("application/json")
                .header("Authorization", token).body(consulta).post(url).then().extract().path("aceptada_por_sunat")));
        // The same generar_comprobante again is idempotent
        given().contentType("application/json").header("Authorization", token).body(factura).post(url).then().statusCode(200)
                .body("numero", equalTo(3));
    }

    @Test
    void nubefact_input_it_cannot_carry_is_refused_and_numbers_are_per_company() {
        String token = as("alice").body(Map.of("label", "ERP")).post(base + "/adapter-tokens/nubefact").then().statusCode(201)
                .extract().path("token");
        as("alice").get(base + "/adapter-tokens").then().statusCode(200).body("label", org.hamcrest.Matchers.hasItem("ERP"))
                .body("token", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.nullValue()));
        String url = "/api/v1/public/nubefact/" + company;
        java.util.function.Function<Map<String, Object>, Map<String, Object>> factura = extra -> {
            Map<String, Object> f = new java.util.HashMap<>(Map.of("operacion", "generar_comprobante", "tipo_de_comprobante", 1, "serie", "F780",
                    "numero", 1, "cliente_tipo_de_documento", "6", "cliente_numero_de_documento", "20111111111",
                    "cliente_denominacion", "CLIENTE SAC", "moneda", 1,
                    "items", List.of(Map.of("unidad_de_medida", "NIU", "descripcion", "Producto", "cantidad", 1, "valor_unitario", 100,
                            "tipo_de_igv", 1))));
            f.putAll(extra);
            return f;
        };
        // Detracción, a 10% IGV, an ISO date, a debit note for exports: refused (422), never a silent 500
        for (Map<String, Object> bad : List.<Map<String, Object>>of(Map.of("sunat_transaction", 30), Map.of("porcentaje_de_igv", 10),
                Map.of("fecha_de_emision", "2027-01-15"), Map.of("moneda", 9), Map.of("total_descuento", "5.00"))) {
            given().contentType("application/json").header("Authorization", token).body(factura.apply(bad)).post(url).then().statusCode(422);
        }
        given().contentType("application/json").header("Authorization", token).body(factura.apply(Map.of())).post(url).then().statusCode(200);

        // Another company of the tenant uses the same number with its own token
        UUID other = companies.verified(tenant, "fake");
        String otherToken = as("alice").body(Map.of("label", "ERP 2")).post("/api/v1/tenants/" + tenant + "/companies/" + other + "/adapter-tokens/nubefact")
                .then().statusCode(201).extract().path("token");
        given().contentType("application/json").header("Authorization", otherToken).body(factura.apply(Map.of())).post("/api/v1/public/nubefact/" + other)
                .then().statusCode(200).body("numero", equalTo(1));
        // A token voids only its own documents
        String apiDoc = as("alice").body(Map.of("serie", "F780", "document", Map.of("kind", "Invoice", "spec", Map.of(
                        "cliente", Map.of("tipo_documento_identidad", "6", "numero_documento_identidad", "20111111111", "nombre", "X"),
                        "detalles", List.of()))))
                .post(base + "/documents").then().statusCode(201).extract().path("serie_numero");
        given().contentType("application/json").header("Authorization", token)
                .body(Map.of("operacion", "generar_anulacion", "tipo_de_comprobante", 1, "serie", "F780", "numero", Long.parseLong(apiDoc.split("-")[1]),
                        "motivo", "Error"))
                .post(url).then().statusCode(409).body("kind", equalTo("not_an_adapter_document"));
    }

    @Test
    void client_numbers_cannot_run_away_with_a_series() {
        String cab = "0101|" + io.github.projectopenubl.xhandler.documents.Deadlines.todayInLima()
                + "|10:30:00|-|0000|6|20111111111|CLIENTE SAC|PEN|18.00|100.00|118.00|0.00|0.00|0.00|118.00|2.1|2.0";
        String det = "NIU|1|P001|-|PRODUCTO|100|18.00|1000|18.00|100.00|IGV|VAT|10|18.00";
        as("alice").body(Map.of("file_name", ruc + "-01-F781-1", "cab", cab, "det", det)).post(base + "/documents/sfs").then().statusCode(201);
        as("alice").body(Map.of("file_name", ruc + "-01-F781-99999999", "cab", cab, "det", det)).post(base + "/documents/sfs").then()
                .statusCode(422);
        // A boleta series for a factura, a bad date, a 10% IGV rate
        as("alice").body(Map.of("file_name", ruc + "-01-B781-1", "cab", cab, "det", det)).post(base + "/documents/sfs").then().statusCode(422);
        as("alice").body(Map.of("file_name", ruc + "-01-F781-2", "cab", cab.replace("|10:30", "x|10:30"), "det", det)).post(base + "/documents/sfs")
                .then().statusCode(422);
        as("alice").body(Map.of("file_name", ruc + "-01-F781-3", "cab", cab, "det", det.replace("|10|18.00", "|10|10.00")))
                .post(base + "/documents/sfs").then().statusCode(422);
    }

    @Test
    void supplier_invoices_are_received_and_checked_with_sunat() throws Exception {
        as("alice").body(Map.of("username", ruc + "USER", "password", "x")).put(base + "/credentials/sol").then().statusCode(200);
        String number = String.valueOf(System.nanoTime() % 1_000_000);
        String xml = "<Invoice><cbc:ID>F100-" + number + "</cbc:ID><ruc>20555555555</ruc><fecha>2027-01-10</fecha><cliente>6|" + ruc
                + "</cliente></Invoice>";
        Path file = Files.createTempFile("reception", ".xml");
        Files.writeString(file, xml, StandardCharsets.UTF_8);
        String token = new KeycloakTestClient().getAccessToken("alice");
        given().auth().oauth2(token).multiPart("xml", file.toFile(), "application/xml").post(base + "/receptions").then().statusCode(201)
                .body("supplier_ruc", equalTo("20555555555")).body("validity_state", equalTo("valid"));
        given().auth().oauth2(token).multiPart("xml", file.toFile(), "application/xml").post(base + "/receptions").then().statusCode(409);
        // A document addressed to someone else
        Files.writeString(file, xml.replace("6|" + ruc, "6|20666666666"), StandardCharsets.UTF_8);
        given().auth().oauth2(token).multiPart("xml", file.toFile(), "application/xml").post(base + "/receptions").then().statusCode(422);
        as("alice").get(base + "/receptions").then().statusCode(200).body("items.supplier_ruc", org.hamcrest.Matchers.hasItem("20555555555"));
    }
}
