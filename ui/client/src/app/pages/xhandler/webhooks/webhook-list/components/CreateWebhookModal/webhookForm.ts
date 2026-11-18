import { array, object, string } from "yup";

export const webhookSchema = object({
  url: string()
    .trim()
    .required("Ingresa la URL")
    .url("Ingresa una URL válida")
    .matches(/^https:\/\//, "La URL debe usar https")
    .max(2000, "Como máximo 2000 caracteres"),
  events: array(string().required())
    .required()
    .min(1, "Elige al menos un evento"),
});

/** `*` covers every event, so picking it alone is enough. */
export const toEvents = (picked: string[]): string[] =>
  picked.includes("*") ? ["*"] : picked;
