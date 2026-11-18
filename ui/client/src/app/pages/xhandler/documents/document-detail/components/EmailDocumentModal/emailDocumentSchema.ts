import { boolean, type InferType, object, string } from "yup";

/** `EmailRequest` takes at most 10 addresses per list. */
export const MAX_RECIPIENTS = 10;

const EMAIL = /^[^\s@,;]+@[^\s@,;]+\.[^\s@,;]+$/;

/** Addresses typed in one field, separated by commas, semicolons or spaces. */
export const splitAddresses = (text: string): string[] =>
  text
    .split(/[\s,;]+/)
    .map((address) => address.trim())
    .filter(Boolean);

const addressList = (field: string) =>
  string()
    .default("")
    .test(
      "addresses",
      `Revisa ${field}: escribe correos válidos separados por comas.`,
      (value) => splitAddresses(value).every((address) => EMAIL.test(address)),
    )
    .test(
      "max",
      `Hasta ${MAX_RECIPIENTS} correos.`,
      (value) => splitAddresses(value).length <= MAX_RECIPIENTS,
    );

export const emailDocumentSchema = object({
  to: addressList("los destinatarios").test(
    "required",
    "Escribe al menos un destinatario.",
    (value) => splitAddresses(value).length > 0,
  ),
  cc: addressList("las copias"),
  includeCdr: boolean().default(false),
});

export type IEmailDocumentValues = InferType<typeof emailDocumentSchema>;
