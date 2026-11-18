import { type InferType, object, string } from "yup";

/** `SenderDomainResource.DomainRequest`'s pattern. */
const DOMAIN = /^[a-z0-9-]+(\.[a-z0-9-]+)+$/i;

export const addSenderDomainSchema = object({
  domain: string()
    .trim()
    .lowercase()
    .required("Escribe el dominio.")
    .matches(
      DOMAIN,
      "Escribe un dominio como empresa.com.pe, sin @ ni https://.",
    ),
});

export type IAddSenderDomainValues = InferType<typeof addSenderDomainSchema>;
