import { boolean, type InferType, object, string } from "yup";

/** SUNAT's RA/RC/RR line takes a motivo of up to 100 characters. */
export const MOTIVO_MAX_LENGTH = 100;

/**
 * The void request (DESIGN.md §11.3): a motivo, the "not delivered" attestation, and the
 * reversal date for retenciones and percepciones (context `needsReversal`).
 */
export const voidDocumentSchema = object({
  motivo: string()
    .trim()
    .required("Escribe el motivo de la anulación.")
    .max(
      MOTIVO_MAX_LENGTH,
      `El motivo admite hasta ${MOTIVO_MAX_LENGTH} caracteres.`,
    ),
  notDelivered: boolean()
    .required()
    .test(
      "not-delivered",
      "Confirma que el comprador no recibió el comprobante.",
      (value) => value === true,
    ),
  reversalDate: string()
    .default("")
    .when("$needsReversal", {
      is: true,
      then: (schema) =>
        schema.required("Indica la fecha en que se comunicó la reversión."),
    }),
});

export type IVoidDocumentValues = InferType<typeof voidDocumentSchema>;
