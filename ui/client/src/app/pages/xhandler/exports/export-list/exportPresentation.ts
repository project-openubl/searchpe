import type { LabelProps } from "@patternfly/react-core";

import type { ExportStatus } from "@app/queries/xhandler/exports";

export const EXPORT_STATUS_PRESENTATION: Record<
  ExportStatus,
  { label: string; color: LabelProps["color"] }
> = {
  pending: { label: "Generando", color: "blue" },
  done: { label: "Lista", color: "green" },
  failed: { label: "Fallida", color: "red" },
};

/** A size in bytes, readable (`1,5 MB`). */
export const formatBytes = (bytes: number | undefined): string => {
  if (bytes === undefined) return "";
  const units = ["B", "KB", "MB", "GB"];
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit += 1;
  }
  return `${value.toLocaleString("es-PE", { maximumFractionDigits: 1 })} ${units[unit]}`;
};
