import type { DraftInput, DraftView } from "@app/client/xhandler";
import { isNoteTipo } from "@app/utils/draftTypes";

import type { IDetalleValues, IDraftFormValues } from "./draftFormSchema";

/** SUNAT catalog 09 (credit) and 10 (debit) reasons the form offers. */
export const TIPOS_NOTA: Record<
  "07" | "08",
  { value: string; label: string }[]
> = {
  "07": [
    { value: "01", label: "Anulación de la operación" },
    { value: "02", label: "Anulación por error en el RUC" },
    { value: "03", label: "Corrección por error en la descripción" },
    { value: "04", label: "Descuento global" },
    { value: "05", label: "Descuento por ítem" },
    { value: "06", label: "Devolución total" },
    { value: "07", label: "Devolución por ítem" },
    { value: "09", label: "Disminución en el valor" },
    { value: "13", label: "Ajustes de montos y/o fechas de pago" },
  ],
  "08": [
    { value: "01", label: "Intereses por mora" },
    { value: "02", label: "Aumento en el valor" },
    { value: "03", label: "Penalidades u otros conceptos" },
  ],
};

export const EMPTY_DETALLE: IDetalleValues = {
  descripcion: "",
  cantidad: "1",
  precio: "",
};

type Spec = Record<string, unknown>;

const asRecord = (value: unknown): Spec =>
  typeof value === "object" && value !== null && !Array.isArray(value)
    ? (value as Spec)
    : {};

const asText = (value: unknown): string =>
  typeof value === "string" || typeof value === "number" ? String(value) : "";

/** The form's values from a draft's xbuilder spec; whatever the form doesn't edit is kept by `toDraftInput`. */
export const toFormValues = (draft: DraftView): IDraftFormValues => {
  const spec = asRecord(draft.spec);
  const cliente = asRecord(spec.cliente);
  const conIgv = (Array.isArray(spec.detalles) ? spec.detalles : []).some(
    (detalle) => asRecord(detalle).precio_con_impuestos !== undefined,
  );
  const detalles = (Array.isArray(spec.detalles) ? spec.detalles : []).map(
    (item): IDetalleValues => {
      const detalle = asRecord(item);
      return {
        descripcion: asText(detalle.descripcion),
        cantidad: asText(detalle.cantidad),
        precio: asText(conIgv ? detalle.precio_con_impuestos : detalle.precio),
      };
    },
  );
  return {
    serie: draft.serie ?? "",
    fecha_emision: asText(spec.fecha_emision),
    moneda: asText(spec.moneda) === "USD" ? "USD" : "PEN",
    precios_con_igv: conIgv,
    cliente_tipo: asText(cliente.tipo_documento_identidad) || "6",
    cliente_numero: asText(cliente.numero_documento_identidad),
    cliente_nombre: asText(cliente.nombre),
    es_nota: isNoteTipo(draft.tipo_comprobante),
    afectado_serie_numero: asText(spec.comprobante_afectado_serie_numero),
    tipo_nota: asText(spec.tipo_nota),
    sustento: asText(spec.descripcion_sustento),
    detalles: detalles.length > 0 ? detalles : [EMPTY_DETALLE],
  };
};

/**
 * The `PUT` body: the draft's spec with the form's fields written over it (fields set through the
 * API and not shown here survive), plus the version edited, so a concurrent change is a 409.
 */
export const toDraftInput = ({
  draft,
  values,
}: {
  draft: DraftView;
  values: IDraftFormValues;
}): DraftInput => {
  const spec: Spec = { ...asRecord(draft.spec) };
  const previousDetalles = Array.isArray(spec.detalles) ? spec.detalles : [];
  spec.moneda = values.moneda;
  if (values.fecha_emision) {
    spec.fecha_emision = values.fecha_emision;
  } else {
    delete spec.fecha_emision;
  }
  spec.cliente = {
    ...asRecord(spec.cliente),
    tipo_documento_identidad: values.cliente_tipo,
    numero_documento_identidad: values.cliente_numero.trim(),
    nombre: values.cliente_nombre.trim(),
  };
  spec.detalles = values.detalles.map((detalle, index) => {
    // Keeps what the API set on the line and the form doesn't show (igv_tipo, unidad_medida…).
    const kept: Spec = { ...asRecord(previousDetalles[index]) };
    delete kept.precio;
    delete kept.precio_con_impuestos;
    return {
      ...kept,
      descripcion: detalle.descripcion.trim(),
      cantidad: Number(detalle.cantidad),
      [values.precios_con_igv ? "precio_con_impuestos" : "precio"]: Number(
        detalle.precio,
      ),
    };
  });
  if (values.es_nota) {
    spec.comprobante_afectado_serie_numero =
      values.afectado_serie_numero.trim();
    spec.tipo_nota = values.tipo_nota;
    spec.descripcion_sustento = values.sustento.trim();
  }
  return {
    kind: draft.kind ?? "Invoice",
    spec,
    serie: values.serie || undefined,
    labels: draft.labels,
    version: draft.version,
  };
};
