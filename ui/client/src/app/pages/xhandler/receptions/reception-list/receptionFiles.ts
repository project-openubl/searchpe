import type { ReceptionFileKind } from "@app/queries/xhandler/receptions";

/** The reception files' names on buttons and menu items. */
export const RECEPTION_FILE_LABELS: Record<ReceptionFileKind, string> = {
  xml: "XML",
  pdf: "PDF",
  cdr: "CDR",
};
