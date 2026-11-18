import { z } from "zod";

import { definePersistentState } from "@app/hooks/usePersistentState";
import {
  isDocumentStatus,
  isTipoComprobante,
} from "@app/queries/xhandler/documents";
import {
  defineListState,
  PAGINATION_STATE,
  schemaFromGuard,
} from "@app/utils/listState";

/** An ISO date (`YYYY-MM-DD`) or "" for none. */
const isoDateOrEmpty = z.union([z.iso.date(), z.literal("")]);

export const DOCUMENT_LIST_STATE = defineListState({
  pagination: PAGINATION_STATE,
  filters: definePersistentState({
    key: "filters",
    schema: z.object({
      // The API filters one type and one status at a time (SingleSelectFilter).
      tipo: z.array(schemaFromGuard(isTipoComprobante)).max(1),
      estado: z.array(schemaFromGuard(isDocumentStatus)).max(1),
      serie: z.string(),
      desde: isoDateOrEmpty,
      hasta: isoDateOrEmpty,
    }),
    defaultValue: { tipo: [], estado: [], serie: "", desde: "", hasta: "" },
  }),
});
