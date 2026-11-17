package io.github.projectopenubl.xhandler.documents;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;

/**
 * SUNAT's QR content for printed representations (DESIGN.md §11.6):
 * {@code RUC|TIPO|SERIE|NUMERO|IGV|TOTAL|FECHA|TIPO_DOC|NUM_DOC|HASH|}. GRE uses SUNAT's own QR.
 */
public final class Qr {

    private Qr() {}

    public static String of(String ruc, Document document, JsonNode enriched) {
        if (document.isGuia() || document.isSummary()) {
            return null;
        }
        JsonNode spec = enriched == null ? null : enriched.path("spec");
        BigDecimal igv = spec == null ? null : decimal(spec.path("total_impuestos").path("gravado_importe"));
        return String.join("|", ruc, document.tipoComprobante, document.serie, String.valueOf(document.numero),
                amount(igv), amount(document.total), document.fechaEmision.toString(), nz(document.customerTipo),
                nz(document.customerNumero), nz(document.digestValue)) + "|";
    }

    private static BigDecimal decimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return new BigDecimal(node.asText());
    }

    private static String amount(BigDecimal value) {
        return value == null ? "0.00" : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
