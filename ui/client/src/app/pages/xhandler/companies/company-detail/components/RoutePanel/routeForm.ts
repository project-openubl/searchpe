import { mixed, object, string } from "yup";

import type { RouteChange, RouteView } from "@app/client/xhandler";
import { SUBMISSION_ROUTES } from "@app/queries/xhandler/companies";

export type SubmissionRoute = (typeof SUBMISSION_ROUTES)[number];

export const isSubmissionRoute = (value: string): value is SubmissionRoute =>
  (SUBMISSION_ROUTES as readonly string[]).includes(value);

export const SUBMISSION_ROUTE_LABELS: Record<SubmissionRoute, string> = {
  sunat: "Directo a SUNAT",
  ose: "A través de un OSE",
};

export interface IRouteFormValues {
  submission_route: SubmissionRoute;
  name: string;
  invoice_url: string;
  perception_url: string;
  username: string;
  password: string;
}

const whenOse = (message: string) =>
  string()
    .defined()
    .trim()
    .when("submission_route", {
      is: "ose",
      then: (schema) => schema.required(message),
    });

export const routeSchema = object({
  submission_route: mixed<SubmissionRoute>()
    .required("Elige una ruta")
    .test("known", "Elige una ruta", (value) =>
      isSubmissionRoute(String(value)),
    ),
  name: whenOse("Ingresa el nombre del OSE").max(
    200,
    "Como máximo 200 caracteres",
  ),
  invoice_url: whenOse("Ingresa la URL de comprobantes del OSE")
    .url("Ingresa una URL válida")
    .max(2000, "Como máximo 2000 caracteres"),
  perception_url: string()
    .defined()
    .trim()
    .url("Ingresa una URL válida")
    .max(2000, "Como máximo 2000 caracteres"),
  username: whenOse("Ingresa el usuario del OSE"),
  password: whenOse("Ingresa la clave del OSE"),
});

/** The form starts from the stored route; the OSE credentials are write-only and start empty. */
export const routeDefaults = (
  route: RouteView | undefined,
): IRouteFormValues => {
  const current = route?.submission_route ?? "";
  return {
    submission_route: isSubmissionRoute(current) ? current : "sunat",
    name: route?.ose_name ?? "",
    invoice_url: route?.ose_invoice_url ?? "",
    perception_url: route?.ose_perception_url ?? "",
    username: "",
    password: "",
  };
};

export const toRouteChange = (values: IRouteFormValues): RouteChange =>
  values.submission_route === "sunat"
    ? { submission_route: "sunat" }
    : {
        submission_route: "ose",
        ose: {
          name: values.name,
          invoice_url: values.invoice_url,
          perception_url: values.perception_url || undefined,
          username: values.username,
          password: values.password,
        },
      };
