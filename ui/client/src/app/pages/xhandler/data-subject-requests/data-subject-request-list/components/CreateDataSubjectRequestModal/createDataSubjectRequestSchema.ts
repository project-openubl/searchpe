import { type InferType, object, string } from "yup";

import { isDataSubjectRequestKind } from "@app/queries/xhandler/data-subject-requests";

/** `DataSubjectRequests.NewRequest`'s constraints, with Spanish messages. */
export const createDataSubjectRequestSchema = object({
  subjectDocType: string()
    .required("Elige el tipo de documento.")
    .matches(/^[0-9A-Z]{1,2}$/, "Elige el tipo de documento."),
  subjectDocNumber: string()
    .trim()
    .required("Escribe el número de documento.")
    .max(20, "El número admite hasta 20 caracteres.")
    .when("subjectDocType", {
      is: "1",
      then: (schema) => schema.matches(/^\d{8}$/, "El DNI tiene 8 dígitos."),
    })
    .when("subjectDocType", {
      is: "6",
      then: (schema) => schema.matches(/^\d{11}$/, "El RUC tiene 11 dígitos."),
    }),
  subjectName: string()
    .trim()
    .default("")
    .max(200, "El nombre admite hasta 200 caracteres."),
  kind: string()
    .required("Elige el derecho que se ejerce.")
    .test("kind", "Elige el derecho que se ejerce.", (value) =>
      isDataSubjectRequestKind(value),
    ),
  details: string()
    .trim()
    .default("")
    .max(2000, "El detalle admite hasta 2000 caracteres."),
});

export type ICreateDataSubjectRequestValues = InferType<
  typeof createDataSubjectRequestSchema
>;
