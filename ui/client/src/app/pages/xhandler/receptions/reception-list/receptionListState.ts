import { z } from "zod";

import { definePersistentState } from "@app/hooks/usePersistentState";
import { defineListState, PAGINATION_STATE } from "@app/utils/listState";

/** An ISO date (`YYYY-MM-DD`) or "" for none. */
const isoDateOrEmpty = z.union([z.iso.date(), z.literal("")]);

export const RECEPTION_LIST_STATE = defineListState({
  pagination: PAGINATION_STATE,
  filters: definePersistentState({
    key: "filters",
    schema: z.object({ desde: isoDateOrEmpty, hasta: isoDateOrEmpty }),
    defaultValue: { desde: "", hasta: "" },
  }),
});
