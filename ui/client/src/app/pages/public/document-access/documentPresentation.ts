/** SUNAT catalog 01 codes the service emits, as the buyer reads them (ADR 0005). */
export const TIPO_COMPROBANTE_LABELS: Record<string, string> = {
  "01": "Factura",
  "03": "Boleta de venta",
  "07": "Nota de crédito",
  "08": "Nota de débito",
  "09": "Guía de remisión remitente",
  "31": "Guía de remisión transportista",
  "20": "Comprobante de retención",
  "40": "Comprobante de percepción",
};

/** The public statuses of `PublicDocumentResource.publicStatus` (plus test documents). */
export const PUBLIC_DOCUMENT_STATUS_LABELS: Record<string, string> = {
  aceptado: "Aceptado por SUNAT",
  rechazado: "Rechazado por SUNAT",
  anulado: "Anulado",
  en_anulacion: "En proceso de anulación",
  pendiente: "Pendiente de validación por SUNAT",
  documento_de_prueba: "Documento de prueba (sin validez tributaria)",
};

/** The document type's name; an unknown code shows as sent. */
export const tipoComprobanteLabel = (code: string | undefined): string =>
  code ? (TIPO_COMPROBANTE_LABELS[code] ?? code) : "Comprobante";

/** The status' Spanish label; an unknown status shows as sent. */
export const publicDocumentStatusLabel = (
  status: string | undefined,
): string => (status ? (PUBLIC_DOCUMENT_STATUS_LABELS[status] ?? status) : "-");

/** The total in its currency, the Peruvian way (`S/ 1,180.00`, `US$ 50.00`). */
export const formatTotal = (
  total: number | undefined,
  moneda: string | undefined,
): string => {
  if (total === undefined || total === null) return "-";
  if (moneda) {
    try {
      return new Intl.NumberFormat("es-PE", {
        style: "currency",
        currency: moneda,
      }).format(total);
    } catch {
      // Not an ISO 4217 code: the code, then the amount.
    }
  }
  const amount = new Intl.NumberFormat("es-PE", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(total);
  return moneda ? `${moneda} ${amount}` : amount;
};
