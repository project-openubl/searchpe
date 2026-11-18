import { number, object, string } from "yup";

/** Document types a company numbers (`DefaultSeries`), with SUNAT's catalog 01 names. */
export const TIPOS_COMPROBANTE: Record<string, string> = {
  "01": "Factura",
  "03": "Boleta de venta",
  "07": "Nota de crédito",
  "08": "Nota de débito",
  "09": "Guía de remisión remitente",
  "31": "Guía de remisión transportista",
  "20": "Comprobante de retención",
  "40": "Comprobante de percepción",
};

/** The type's name, or the code as returned for one the UI doesn't know. */
export const tipoComprobanteLabel = (code: string | undefined): string =>
  code ? (TIPOS_COMPROBANTE[code] ?? code) : "-";

export const seriesSchema = object({
  tipo_comprobante: string()
    .required("Elige el tipo de comprobante")
    .matches(/^\d{2}$/, "Elige el tipo de comprobante"),
  serie: string()
    .trim()
    .required("Ingresa la serie")
    .matches(
      /^[A-Z0-9]{4}$/,
      "La serie tiene 4 caracteres en mayúsculas o dígitos (F001)",
    ),
  next_number: number()
    .transform((value: number, original: unknown) =>
      original === "" || original === undefined ? undefined : value,
    )
    .typeError("Ingresa un número")
    .integer("Ingresa un número entero")
    .min(1, "El número empieza en 1"),
});

/**
 * The next number of an existing series: it only moves forward (numbers already used are never
 * reused, so the API answers `409 series_number_used` below them) and has at most 8 digits.
 */
export const seriesNextNumberSchema = (current: number) =>
  object({
    next_number: number()
      .transform((value: number, original: unknown) =>
        original === "" || original === undefined ? undefined : value,
      )
      .typeError("Ingresa un número")
      .required("Ingresa el siguiente número")
      .integer("Ingresa un número entero")
      .min(
        current,
        `El siguiente número no puede ser menor que ${current.toLocaleString("es-PE")}`,
      )
      .max(99_999_999, "El número tiene como máximo 8 dígitos"),
  });
