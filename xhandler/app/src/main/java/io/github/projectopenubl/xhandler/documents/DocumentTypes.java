package io.github.projectopenubl.xhandler.documents;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.projectopenubl.xhandler.api.ApiException;

/** Maps xbuilder kinds to SUNAT's catalog 01 (DESIGN.md §1.3). */
public final class DocumentTypes {

    private DocumentTypes() {}

    /** Catalog 01 code of a document clients may emit. Summaries are built by the service only. */
    public static String tipoComprobante(String kind, JsonNode spec) {
        return switch (kind) {
            case "Invoice" -> {
                String tipo = spec.path("tipo_comprobante").asText("");
                if (tipo.isEmpty()) {
                    tipo = spec.path("serie_numero").asText("").startsWith("B") ? "03" : "01";
                }
                if (!tipo.equals("01") && !tipo.equals("03")) {
                    throw ApiException.invalid("invalid_document", "tipo_comprobante debe ser 01 (factura) o 03 (boleta).", null);
                }
                yield tipo;
            }
            case "CreditNote" -> "07";
            case "DebitNote" -> "08";
            case "DespatchAdvice" -> {
                String tipo = spec.path("tipo_comprobante").asText("09");
                if (!tipo.equals("09") && !tipo.equals("31")) {
                    throw ApiException.invalid("invalid_document", "tipo_comprobante debe ser 09 (remitente) o 31 (transportista).", null);
                }
                yield tipo;
            }
            case "Perception" -> "40";
            case "Retention" -> "20";
            case "SummaryDocuments", "VoidedDocuments", "Reversion" -> throw ApiException.invalid("invalid_document",
                    "Los resúmenes y comunicaciones de baja los genera el servicio (use la anulación del documento).", null);
            default -> throw ApiException.invalid("invalid_document", "Tipo de documento desconocido: " + kind + ".", null);
        };
    }

    /** The buyer's name, for listings (customer, destinatario or proveedor of a perception/retention). */
    public static String customerName(JsonNode spec) {
        for (String field : new String[] {"cliente", "destinatario", "proveedor_receptor"}) {
            JsonNode node = spec.path(field).path("nombre");
            if (!node.isMissingNode() && !node.isNull()) {
                return node.asText();
            }
        }
        return null;
    }
}
