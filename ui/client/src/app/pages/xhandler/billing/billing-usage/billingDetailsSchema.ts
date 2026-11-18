import { object, string } from "yup";

/** Empty fields are allowed (the API accepts them as unset); a RUC has SUNAT's 11 digits. */
export const billingDetailsSchema = object({
  billing_ruc: string()
    .trim()
    .matches(/^(\d{11})?$/, "El RUC tiene 11 dígitos"),
  billing_name: string().trim().max(300, "Como máximo 300 caracteres"),
  billing_email: string().trim().email("Ingresa un correo válido"),
});
