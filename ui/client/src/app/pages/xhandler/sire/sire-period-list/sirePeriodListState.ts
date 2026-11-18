import { z } from "zod";

import { definePersistentState } from "@app/hooks/usePersistentState";
import { isSireBook } from "@app/queries/xhandler/sire";
import {
  defineListState,
  PAGINATION_STATE,
  schemaFromGuard,
} from "@app/utils/listState";

export const SIRE_PERIOD_LIST_STATE = defineListState({
  pagination: PAGINATION_STATE,
  filters: definePersistentState({
    key: "filters",
    schema: z.object({
      // The API filters one register at a time: a list of at most one (SingleSelectFilter).
      libro: z.array(schemaFromGuard(isSireBook)).max(1),
    }),
    defaultValue: { libro: [] },
  }),
});
