package io.github.projectopenubl.xhandler.adapters;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.projectopenubl.xhandler.api.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * SUNAT's SFS (Facturador SUNAT) TXT files to native xbuilder JSON (DESIGN.md §5). The file name is
 * {@code RUC-TT-SERIE-NUMERO}; facturas and boletas come as .CAB + .DET, notes as .NOT + .DET, fields
 * separated by "|". Only the fields xbuilder needs are read: it recomputes every total.
 */
public final class SfsAdapter {

    /** A converted document: xbuilder JSON plus the client's serie and number. */
    public record Converted(ObjectNode document, String ruc, String tipo, String serie, long numero) {}

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private SfsAdapter() {}

    /**
     * @param fileName {@code 20123456789-01-F001-123} (extension optional)
     * @param header the .CAB (01/03) or .NOT (07/08) content
     * @param details the .DET content, one line per item
     */
    public static Converted convert(String fileName, String header, String details) {
        String[] name = fileName.replaceAll("\\.[A-Za-z]+$", "").split("-");
        if (name.length != 4 || !name[0].matches("\\d{11}") || !name[1].matches("\\d{2}") || !name[3].matches("\\d{1,8}")) {
            throw invalid("El nombre del archivo debe ser RUC-TIPO-SERIE-NUMERO.");
        }
        String tipo = name[1];
        String[] h = fields(firstLine(header));
        ObjectNode spec = JSON.objectNode();
        String kind;
        switch (tipo) {
            case "01", "03" -> {
                require(h, 16, ".CAB");
                kind = "Invoice";
                spec.put("tipo_comprobante", tipo);
                spec.put("tipo_operacion", h[0]);
                spec.put("fecha_emision", LocalDate.parse(h[1]).toString());
                if (!h[3].isBlank() && !"-".equals(h[3])) {
                    spec.put("fecha_vencimiento", LocalDate.parse(h[3]).toString());
                }
                spec.set("cliente", cliente(h[5], h[6], h[7]));
                spec.put("moneda", h[8]);
            }
            case "07", "08" -> {
                require(h, 12, ".NOT");
                kind = "07".equals(tipo) ? "CreditNote" : "DebitNote";
                spec.put("fecha_emision", LocalDate.parse(h[1]).toString());
                spec.set("cliente", cliente(h[4], h[5], h[6]));
                spec.put("moneda", h[7]);
                spec.put("tipo_nota", h[8]);
                spec.put("descripcion_sustento", h[9]);
                spec.put("comprobante_afectado_tipo", h[10]);
                spec.put("comprobante_afectado_serie_numero", h[11]);
            }
            default -> throw invalid("Tipo de comprobante no soportado por el adaptador SFS: " + tipo + ".");
        }
        ArrayNode detalles = spec.putArray("detalles");
        for (String line : details.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            String[] d = fields(line);
            require(d, 14, ".DET");
            ObjectNode detalle = detalles.addObject();
            detalle.put("unidad_medida", d[0]);
            detalle.put("cantidad", new BigDecimal(d[1]));
            if (!d[2].isBlank()) {
                detalle.put("codigo", d[2]);
            }
            detalle.put("descripcion", d[4]);
            detalle.put("precio", new BigDecimal(d[5]));
            detalle.put("igv_tipo", d[12]);
        }
        if (detalles.isEmpty()) {
            throw invalid("El archivo .DET no tiene ítems.");
        }
        ObjectNode document = JSON.objectNode();
        document.put("kind", kind);
        document.set("spec", spec);
        return new Converted(document, name[0], tipo, name[2], Long.parseLong(name[3]));
    }

    private static ObjectNode cliente(String tipo, String numero, String nombre) {
        ObjectNode cliente = JSON.objectNode();
        cliente.put("tipo_documento_identidad", tipo);
        cliente.put("numero_documento_identidad", numero);
        cliente.put("nombre", nombre);
        return cliente;
    }

    private static String firstLine(String text) {
        return text == null ? "" : text.strip().split("\\R")[0];
    }

    private static String[] fields(String line) {
        return line.split("\\|", -1);
    }

    private static void require(String[] fields, int count, String file) {
        if (fields.length < count) {
            throw invalid("El archivo " + file + " tiene " + fields.length + " campos; se esperaban al menos " + count + ".");
        }
    }

    private static ApiException invalid(String message) {
        return ApiException.invalid("invalid_document", message, null);
    }
}
