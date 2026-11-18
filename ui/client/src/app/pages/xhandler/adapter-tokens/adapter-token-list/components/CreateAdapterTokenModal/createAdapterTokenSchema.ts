import { type InferType, object, string } from "yup";

export const createAdapterTokenSchema = object({
  label: string()
    .trim()
    .required("Escribe una etiqueta para reconocer el token.")
    .max(100, "La etiqueta admite hasta 100 caracteres."),
});

export type ICreateAdapterTokenValues = InferType<
  typeof createAdapterTokenSchema
>;
