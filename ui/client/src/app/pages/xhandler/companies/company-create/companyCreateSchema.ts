import { object, string } from "yup";

/** The create form's fields; RUC is SUNAT's fixed 11 digits, the server decides the rest. */
export const companyCreateSchema = object({
  ruc: string()
    .trim()
    .required("Ingresa el RUC")
    .matches(/^\d{11}$/, "El RUC tiene 11 dígitos"),
  razon_social: string()
    .trim()
    .required("Ingresa la razón social")
    .max(300, "Como máximo 300 caracteres"),
  nombre_comercial: string().trim().max(300, "Como máximo 300 caracteres"),
});
