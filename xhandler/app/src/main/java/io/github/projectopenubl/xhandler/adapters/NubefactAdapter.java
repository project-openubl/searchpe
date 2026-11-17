package io.github.projectopenubl.xhandler.adapters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.projectopenubl.xhandler.api.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Nubefact-compatible JSON to native xbuilder JSON (DESIGN.md §5), so Nubefact's customers can switch
 * by changing only the URL and token. Only the fields xbuilder needs are read (it recomputes totals).
 */
public final class NubefactAdapter {

    public record Converted(ObjectNode document, String tipo, String serie, long numero) {}

    static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    /** Nubefact's tipo_de_comprobante. */
    static final Map<String, String> TIPOS = Map.of("1", "01", "2", "03", "3", "07", "4", "08");
    static final Map<String, String> MONEDAS = Map.of("1", "PEN", "2", "USD", "3", "EUR", "4", "GBP");
    /** Nubefact's tipo_de_igv to SUNAT's catalog 07. */
    static final Map<String, String> IGV = Map.ofEntries(Map.entry("1", "10"), Map.entry("2", "11"), Map.entry("3", "12"),
            Map.entry("4", "13"), Map.entry("5", "14"), Map.entry("6", "15"), Map.entry("7", "16"), Map.entry("8", "20"),
            Map.entry("9", "30"), Map.entry("10", "31"), Map.entry("11", "32"), Map.entry("12", "33"), Map.entry("13", "34"),
            Map.entry("14", "35"), Map.entry("15", "36"), Map.entry("16", "40"), Map.entry("17", "21"));

    private NubefactAdapter() {}

    public static String tipo(JsonNode request) {
        String tipo = TIPOS.get(request.path("tipo_de_comprobante").asText());
        if (tipo == null) {
            throw invalid("tipo_de_comprobante debe ser 1, 2, 3 o 4.");
        }
        return tipo;
    }

    public static Converted convert(JsonNode r) {
        String tipo = tipo(r);
        String serie = r.path("serie").asText();
        long numero = r.path("numero").asLong(0);
        ObjectNode spec = JSON.objectNode();
        String kind = switch (tipo) {
            case "07" -> "CreditNote";
            case "08" -> "DebitNote";
            default -> "Invoice";
        };
        if ("Invoice".equals(kind)) {
            spec.put("tipo_comprobante", tipo);
        }
        if (r.hasNonNull("fecha_de_emision")) {
            spec.put("fecha_emision", LocalDate.parse(r.get("fecha_de_emision").asText(), DMY).toString());
        }
        if (r.hasNonNull("fecha_de_vencimiento") && !r.get("fecha_de_vencimiento").asText().isBlank()) {
            spec.put("fecha_vencimiento", LocalDate.parse(r.get("fecha_de_vencimiento").asText(), DMY).toString());
        }
        spec.put("moneda", MONEDAS.getOrDefault(r.path("moneda").asText("1"), "PEN"));
        if (r.hasNonNull("tipo_de_cambio") && !r.get("tipo_de_cambio").asText().isBlank()) {
            spec.put("tipo_cambio", new BigDecimal(r.get("tipo_de_cambio").asText()));
        }
        ObjectNode cliente = spec.putObject("cliente");
        cliente.put("tipo_documento_identidad", r.path("cliente_tipo_de_documento").asText("-"));
        cliente.put("numero_documento_identidad", r.path("cliente_numero_de_documento").asText("-"));
        cliente.put("nombre", r.path("cliente_denominacion").asText("Clientes varios"));
        if (r.hasNonNull("cliente_direccion") && !r.get("cliente_direccion").asText().isBlank()) {
            cliente.putObject("direccion").put("direccion", r.get("cliente_direccion").asText());
        }
        if (r.hasNonNull("cliente_email") && !r.get("cliente_email").asText().isBlank()) {
            cliente.putObject("contacto").put("email", r.get("cliente_email").asText());
        }
        if (r.hasNonNull("observaciones") && !r.get("observaciones").asText().isBlank()) {
            spec.put("observaciones", r.get("observaciones").asText());
        }
        if (!"Invoice".equals(kind)) {
            String afectadoTipo = TIPOS.get(r.path("documento_que_se_modifica_tipo").asText());
            if (afectadoTipo == null) {
                throw invalid("documento_que_se_modifica_tipo debe ser 1 o 2.");
            }
            spec.put("comprobante_afectado_tipo", afectadoTipo);
            spec.put("comprobante_afectado_serie_numero",
                    r.path("documento_que_se_modifica_serie").asText() + "-" + r.path("documento_que_se_modifica_numero").asText());
            String motivo = "07".equals(tipo) ? r.path("tipo_de_nota_de_credito").asText() : r.path("tipo_de_nota_de_debito").asText();
            spec.put("tipo_nota", String.format("%02d", Integer.parseInt(motivo.isBlank() ? "1" : motivo)));
            spec.put("descripcion_sustento", r.path("observaciones").asText("Nota " + tipo));
        }
        ArrayNode detalles = spec.putArray("detalles");
        for (JsonNode item : r.path("items")) {
            ObjectNode detalle = detalles.addObject();
            detalle.put("unidad_medida", item.path("unidad_de_medida").asText("NIU"));
            if (item.hasNonNull("codigo") && !item.get("codigo").asText().isBlank()) {
                detalle.put("codigo", item.get("codigo").asText());
            }
            detalle.put("descripcion", item.path("descripcion").asText());
            detalle.put("cantidad", new BigDecimal(item.path("cantidad").asText("1")));
            detalle.put("precio", new BigDecimal(item.path("valor_unitario").asText("0")));
            String igv = IGV.get(item.path("tipo_de_igv").asText("1"));
            if (igv == null) {
                throw invalid("tipo_de_igv inválido: " + item.path("tipo_de_igv").asText());
            }
            detalle.put("igv_tipo", igv);
        }
        if (detalles.isEmpty()) {
            throw invalid("El comprobante no tiene ítems.");
        }
        ObjectNode document = JSON.objectNode();
        document.put("kind", kind);
        document.set("spec", spec);
        return new Converted(document, tipo, serie, numero);
    }

    /** Nubefact's answer shape from one of our documents. */
    public static ObjectNode answer(JsonNode view, String pdfUrl, String xmlUrl, String cdrUrl) {
        ObjectNode a = JSON.objectNode();
        String tipo = view.path("tipo_comprobante").asText();
        a.put("tipo_de_comprobante", TIPOS.entrySet().stream().filter(e -> e.getValue().equals(tipo)).map(Map.Entry::getKey)
                .findFirst().orElse(tipo));
        a.put("serie", view.path("serie").asText());
        a.put("numero", view.path("numero").asLong());
        a.put("enlace", view.path("access_url").asText(null));
        String status = view.path("status").asText();
        a.put("aceptada_por_sunat", status.startsWith("accepted"));
        a.put("sunat_description", view.path("sunat_description").asText(null));
        a.put("sunat_note", view.path("sunat_notes").isArray() && !view.path("sunat_notes").isEmpty()
                ? view.path("sunat_notes").get(0).asText() : null);
        a.put("sunat_responsecode", view.path("sunat_code").isNull() || view.path("sunat_code").isMissingNode() ? null
                : view.path("sunat_code").asText());
        a.put("sunat_soap_error", "");
        a.put("anulado", "voided".equals(status));
        a.put("cadena_para_codigo_qr", view.path("qr").asText(null));
        a.put("codigo_hash", view.path("digest_value").asText(null));
        a.put("enlace_del_pdf", pdfUrl);
        a.put("enlace_del_xml", xmlUrl);
        a.put("enlace_del_cdr", cdrUrl);
        a.put("estado_xhandler", status);
        return a;
    }

    static ApiException invalid(String message) {
        return ApiException.invalid("invalid_document", message, null);
    }
}
