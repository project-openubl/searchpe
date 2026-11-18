import { object, string } from "yup";

/** `PUT /tenants/{tenantId}`: the organization's name, 1–200 characters. */
export const tenantNameSchema = object({
  name: string()
    .trim()
    .required("Ingresa el nombre de la organización")
    .max(200, "Como máximo 200 caracteres"),
});
