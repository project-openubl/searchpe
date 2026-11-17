package io.github.projectopenubl.xhandler.adapters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.projectopenubl.xhandler.api.ApiException;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Golden files of the input adapters (DESIGN.md §15.2): input → native xbuilder JSON. */
class AdapterGoldenTest {

    static final ObjectMapper JSON = new ObjectMapper();

    static JsonNode golden(String name) throws IOException {
        return JSON.readTree(Files.readString(Path.of("src/test/resources/adapters", name)));
    }

    /** Numbers compare by value (50 and 50.00 are the same amount). */
    static void assertSameJson(JsonNode expected, JsonNode actual) {
        assertEquals(normalize(expected), normalize(actual));
    }

    static JsonNode normalize(JsonNode node) {
        if (node.isNumber()) {
            return JSON.getNodeFactory().numberNode(new BigDecimal(node.asText()).stripTrailingZeros());
        }
        if (node.isObject()) {
            var copy = JSON.createObjectNode();
            node.fields().forEachRemaining(e -> copy.set(e.getKey(), normalize(e.getValue())));
            return copy;
        }
        if (node.isArray()) {
            var copy = JSON.createArrayNode();
            node.forEach(n -> copy.add(normalize(n)));
            return copy;
        }
        return node;
    }

    @Test
    void sfs_factura() throws IOException {
        String cab = "0101|2027-01-15|10:30:00|-|0000|6|20111111111|CLIENTE SAC|PEN|18.00|200.00|218.00|0.00|0.00|0.00|218.00|2.1|2.0";
        String det = """
                NIU|2|P001|-|PRODUCTO UNO|50.00|18.00|1000|18.00|100.00|IGV|VAT|10|18.00|-|0.00|0.00|||0|-|0.00|0.00|||0|59.00|100.00|0.00
                ZZ|1||-|SERVICIO|100|0.00|9997|0.00|100.00|EXO|VAT|20|0.00|-|0.00|0.00|||0|-|0.00|0.00|||0|100.00|100.00|0.00""";
        SfsAdapter.Converted converted = SfsAdapter.convert("20123456789-01-F001-123.CAB", cab, det);
        assertEquals("F001", converted.serie());
        assertEquals(123, converted.numero());
        assertEquals("20123456789", converted.ruc());
        assertSameJson(golden("sfs-invoice.json"), converted.document());
    }

    @Test
    void sfs_rejects_a_bad_file_name() {
        assertThrows(ApiException.class, () -> SfsAdapter.convert("factura.CAB", "x", "y"));
    }

    @Test
    void nubefact_credit_note() throws IOException {
        JsonNode request = JSON.readTree("""
                {"operacion":"generar_comprobante","tipo_de_comprobante":3,"serie":"FC01","numero":7,"sunat_transaction":1,
                 "cliente_tipo_de_documento":"6","cliente_numero_de_documento":"20111111111","cliente_denominacion":"CLIENTE SAC",
                 "cliente_email":"compras@cliente.pe","fecha_de_emision":"15-01-2027","moneda":2,"porcentaje_de_igv":18.00,
                 "observaciones":"Devolución parcial","documento_que_se_modifica_tipo":1,"documento_que_se_modifica_serie":"F001",
                 "documento_que_se_modifica_numero":15,"tipo_de_nota_de_credito":7,
                 "items":[{"unidad_de_medida":"NIU","codigo":"P1","descripcion":"Producto","cantidad":1,"valor_unitario":100,
                           "precio_unitario":118,"subtotal":100,"tipo_de_igv":1,"igv":18,"total":118}]}""");
        NubefactAdapter.Converted converted = NubefactAdapter.convert(request);
        assertEquals("07", converted.tipo());
        assertEquals("FC01", converted.serie());
        assertEquals(7, converted.numero());
        assertSameJson(golden("nubefact-credit-note.json"), converted.document());
    }

    @Test
    void nubefact_maps_igv_types() throws IOException {
        JsonNode request = JSON.readTree("""
                {"tipo_de_comprobante":2,"serie":"B001","numero":1,"items":[
                 {"descripcion":"a","cantidad":1,"valor_unitario":1,"tipo_de_igv":8},
                 {"descripcion":"b","cantidad":1,"valor_unitario":1,"tipo_de_igv":16},
                 {"descripcion":"c","cantidad":1,"valor_unitario":1,"tipo_de_igv":17}]}""");
        JsonNode detalles = NubefactAdapter.convert(request).document().path("spec").path("detalles");
        assertEquals("20", detalles.get(0).path("igv_tipo").asText());
        assertEquals("40", detalles.get(1).path("igv_tipo").asText());
        assertEquals("21", detalles.get(2).path("igv_tipo").asText());
    }
}
