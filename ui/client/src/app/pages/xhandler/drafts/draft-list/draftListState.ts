import { z } from "zod";

import { definePersistentState } from "@app/hooks/usePersistentState";
import { isDraftStatus } from "@app/queries/xhandler/drafts";
import {
  defineListState,
  PAGINATION_STATE,
  schemaFromGuard,
} from "@app/utils/listState";

export const DRAFT_LIST_STATE = defineListState({
  pagination: PAGINATION_STATE,
  filters: definePersistentState({
    key: "filters",
    schema: z.object({
      // The API filters one status at a time: a list of at most one (SingleSelectFilter).
      estado: z.array(schemaFromGuard(isDraftStatus)).max(1),
    }),
    defaultValue: { estado: [] },
  }),
});
