import type {
  DataSubjectRequestKind,
  DataSubjectRequestStatus,
} from "@app/queries/xhandler/data-subject-requests";

/** The ARCO rights (Ley 29733) in Spanish. */
export const DATA_SUBJECT_REQUEST_KIND_LABELS: Record<
  DataSubjectRequestKind,
  string
> = {
  access: "Acceso",
  rectification: "Rectificación",
  cancellation: "Cancelación",
  opposition: "Oposición",
};

export const DATA_SUBJECT_REQUEST_STATUS_LABELS: Record<
  DataSubjectRequestStatus,
  string
> = {
  open: "Abierta",
  closed: "Cerrada",
};

/** SUNAT catalog 06 identity document types offered for the person. */
export const SUBJECT_DOC_TYPES = [
  { value: "1", label: "DNI" },
  { value: "4", label: "Carné de extranjería" },
  { value: "7", label: "Pasaporte" },
  { value: "6", label: "RUC" },
  { value: "A", label: "Cédula diplomática" },
  { value: "0", label: "Documento tributario no domiciliado" },
] as const;

/** The document type's name; an unknown code shows as returned. */
export const subjectDocTypeLabel = (code: string): string =>
  SUBJECT_DOC_TYPES.find((type) => type.value === code)?.label ?? code;

/** Whether an open request is past its legal term. */
export const isOverdue = ({
  status,
  responseDueAt,
  now,
}: {
  status: string;
  responseDueAt: string;
  now: Date;
}): boolean =>
  status === "open" &&
  !!responseDueAt &&
  new Date(responseDueAt).getTime() < now.getTime();
