import type { TipoComprobante } from "@app/queries/xhandler/documents";
import { isTipoComprobante } from "@app/queries/xhandler/documents";

/** SUNAT catalog 01 names of the types the console lists (ADR 0005). */
export const TIPO_COMPROBANTE_LABELS: Record<TipoComprobante, string> = {
  "01": "Factura",
  "03": "Boleta de venta",
  "07": "Nota de crédito",
  "08": "Nota de débito",
  "09": "Guía de remisión remitente",
  "31": "Guía de remisión transportista",
  "20": "Comprobante de retención",
  "40": "Comprobante de percepción",
  RC: "Resumen diario",
  RA: "Comunicación de baja",
  RR: "Resumen de reversiones",
};

/** The type's name; an unknown code shows as returned. */
export const tipoComprobanteLabel = (code: string | undefined): string => {
  if (!code) return "-";
  return isTipoComprobante(code) ? TIPO_COMPROBANTE_LABELS[code] : code;
};

/** An amount in its currency, the Peruvian way (`S/ 1,180.00`, `US$ 50.00`). */
export const formatTotal = ({
  total,
  moneda,
}: {
  total: number | string | undefined | null;
  moneda: string | undefined | null;
}): string => {
  const amount = typeof total === "string" ? Number(total) : total;
  if (amount === undefined || amount === null || Number.isNaN(amount)) {
    return "-";
  }
  if (moneda) {
    try {
      return new Intl.NumberFormat("es-PE", {
        style: "currency",
        currency: moneda,
      }).format(amount);
    } catch {
      // Not an ISO 4217 code: the code, then the amount.
    }
  }
  const text = new Intl.NumberFormat("es-PE", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount);
  return moneda ? `${moneda} ${text}` : text;
};
