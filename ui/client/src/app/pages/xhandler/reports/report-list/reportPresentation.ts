import type { LabelProps } from "@patternfly/react-core";

import type {
  ReportFormat,
  ReportRegister,
  ReportStatus,
} from "@app/queries/xhandler/reports";

export const REPORT_REGISTER_LABELS: Record<ReportRegister, string> = {
  sales: "Registro de ventas",
  purchases: "Registro de compras",
};

export const REPORT_FORMAT_LABELS: Record<ReportFormat, string> = {
  csv: "CSV",
  xlsx: "Excel",
  ple: "PLE (libros electrónicos)",
  sire: "SIRE (reemplazo de propuesta)",
};

export const REPORT_STATUS_PRESENTATION: Record<
  ReportStatus,
  { label: string; color: LabelProps["color"] }
> = {
  pending: { label: "Generando", color: "blue" },
  done: { label: "Listo", color: "green" },
  failed: { label: "Fallido", color: "red" },
};

/** `YYYYMM` as SUNAT's period is read (`03/2026`). */
export const formatPeriod = (period: string | undefined): string =>
  period && /^\d{6}$/.test(period)
    ? `${period.slice(4)}/${period.slice(0, 4)}`
    : (period ?? "-");
