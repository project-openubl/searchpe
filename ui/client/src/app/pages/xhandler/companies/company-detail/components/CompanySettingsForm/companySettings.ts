import { boolean, type InferType, mixed, object, string } from "yup";

import type { CompanyView, Settings } from "@app/client/xhandler";
import { PDF_FORMATS } from "@app/queries/xhandler/companies";
import { isHttpsUrl } from "@app/utils/url";

export type PdfFormat = (typeof PDF_FORMATS)[number];

export const isPdfFormat = (value: string): value is PdfFormat =>
  (PDF_FORMATS as readonly string[]).includes(value);

const toPdfFormat = (value: string | undefined): PdfFormat =>
  value && isPdfFormat(value) ? value : "a4";

export const PDF_FORMAT_LABELS: Record<PdfFormat, string> = {
  a4: "A4",
  ticket80: "Ticket 80 mm",
  ticket58: "Ticket 58 mm",
};

export const companySettingsSchema = object({
  nombre_comercial: string()
    .defined()
    .trim()
    .max(300, "Como máximo 300 caracteres"),
  email: string()
    .defined()
    .trim()
    .email("Ingresa un correo válido")
    .max(300, "Como máximo 300 caracteres"),
  direccion: string().defined().trim().max(500, "Como máximo 500 caracteres"),
  pdf_format: mixed<PdfFormat>()
    .required("Elige un formato")
    .test("known", "Elige un formato", (value) => isPdfFormat(String(value))),
  delivered_on_emit_default: boolean().required(),
  email_on_accept: boolean().required(),
  email_subject: string()
    .defined()
    .max(200, "Como máximo 200 caracteres")
    .matches(/^[^\r\n]*$/, "Debe ser una sola línea"),
  email_body: string().defined().max(5000, "Como máximo 5000 caracteres"),
  libro_reclamaciones_url: string()
    .defined()
    .trim()
    .max(500, "Como máximo 500 caracteres")
    .test(
      "https",
      "Ingresa una dirección https válida",
      (value) => !value || isHttpsUrl(value),
    ),
});

export type ICompanySettingsValues = InferType<typeof companySettingsSchema>;

/** The form's starting values, from the company as the API returns it. */
export const settingsDefaults = (
  company: CompanyView,
): ICompanySettingsValues => ({
  nombre_comercial: company.nombre_comercial ?? "",
  email: company.email ?? "",
  direccion: company.direccion ?? "",
  pdf_format: toPdfFormat(company.pdf_format),
  delivered_on_emit_default: company.delivered_on_emit_default ?? false,
  email_on_accept: company.email_on_accept ?? false,
  email_subject: company.email_subject ?? "",
  email_body: company.email_body ?? "",
  libro_reclamaciones_url: company.libro_reclamaciones_url ?? "",
});

/**
 * `PUT …/settings` leaves out what is null, so only the changed fields are sent: a field the user
 * didn't touch keeps its stored value.
 */
export const settingsChanges = ({
  values,
  dirty,
}: {
  values: ICompanySettingsValues;
  dirty: Partial<Record<keyof ICompanySettingsValues, boolean | undefined>>;
}): Settings => {
  const changes: Settings = {};
  if (dirty.nombre_comercial)
    changes.nombre_comercial = values.nombre_comercial;
  if (dirty.email) changes.email = values.email;
  if (dirty.direccion) changes.direccion = values.direccion;
  if (dirty.pdf_format) changes.pdf_format = values.pdf_format;
  if (dirty.delivered_on_emit_default) {
    changes.delivered_on_emit_default = values.delivered_on_emit_default;
  }
  if (dirty.email_on_accept) changes.email_on_accept = values.email_on_accept;
  if (dirty.email_subject) changes.email_subject = values.email_subject;
  if (dirty.email_body) changes.email_body = values.email_body;
  // Blank is sent as is: the service removes the address.
  if (dirty.libro_reclamaciones_url) {
    changes.libro_reclamaciones_url = values.libro_reclamaciones_url;
  }
  return changes;
};
