import { array, boolean, type InferType, object, string } from "yup";

const amount = (message: string) =>
  string()
    .trim()
    .required(message)
    .matches(/^\d+(\.\d+)?$/, "Usa solo números y punto decimal")
    .test("positive", "Debe ser mayor que cero", (value) => Number(value) > 0);

/** SUNAT catalog 06 codes the form offers, with the format SUNAT fixes for each. */
export const TIPOS_DOCUMENTO_IDENTIDAD = [
  { value: "6", label: "RUC" },
  { value: "1", label: "DNI" },
  { value: "4", label: "Carné de extranjería" },
  { value: "7", label: "Pasaporte" },
  { value: "0", label: "Sin documento (no domiciliado)" },
] as const;

/** The draft form's fields: SUNAT's fixed formats are checked here, the service decides the rest. */
export const draftFormSchema = object({
  serie: string().defined(),
  fecha_emision: string()
    .defined()
    .matches(/^(\d{4}-\d{2}-\d{2})?$/, "Usa el formato AAAA-MM-DD"),
  moneda: string().oneOf(["PEN", "USD"]).required("Elige la moneda"),
  precios_con_igv: boolean().required(),
  cliente_tipo: string().required("Elige el tipo de documento"),
  cliente_numero: string()
    .trim()
    .required("Ingresa el número de documento")
    .when("cliente_tipo", ([tipo], schema) => {
      if (tipo === "6") {
        return schema.matches(/^\d{11}$/, "El RUC tiene 11 dígitos");
      }
      if (tipo === "1") {
        return schema.matches(/^\d{8}$/, "El DNI tiene 8 dígitos");
      }
      return schema.max(15, "Como máximo 15 caracteres");
    }),
  cliente_nombre: string()
    .trim()
    .required("Ingresa el nombre o razón social")
    .max(300, "Como máximo 300 caracteres"),
  // Notes only: the schema reads `es_nota`, set from the draft's type and never shown.
  es_nota: boolean().required(),
  afectado_serie_numero: string()
    .trim()
    .defined()
    .when("es_nota", ([esNota], schema) =>
      esNota
        ? schema
            .required("Ingresa el comprobante que corrige")
            .matches(
              /^[A-Z0-9]{4}-\d{1,8}$/,
              "Usa el formato serie-número, por ejemplo F001-25",
            )
        : schema,
    ),
  tipo_nota: string()
    .defined()
    .when("es_nota", ([esNota], schema) =>
      esNota ? schema.required("Elige el motivo") : schema,
    ),
  sustento: string()
    .trim()
    .defined()
    .max(500, "Como máximo 500 caracteres")
    .when("es_nota", ([esNota], schema) =>
      esNota ? schema.required("Describe el sustento") : schema,
    ),
  detalles: array(
    object({
      descripcion: string()
        .trim()
        .required("Ingresa la descripción")
        .max(500, "Como máximo 500 caracteres"),
      cantidad: amount("Ingresa la cantidad"),
      precio: amount("Ingresa el precio"),
    }),
  )
    .required()
    .min(1, "Agrega al menos un detalle"),
});

export type IDraftFormValues = InferType<typeof draftFormSchema>;

export type IDetalleValues = IDraftFormValues["detalles"][number];
