import { z } from "zod";

import { definePersistentState } from "@app/hooks/usePersistentState";
import { isConformityState } from "@app/queries/xhandler/conformity";
import {
  defineListState,
  PAGINATION_STATE,
  schemaFromGuard,
} from "@app/utils/listState";

export const CONFORMITY_LIST_STATE = defineListState({
  pagination: PAGINATION_STATE,
  filters: definePersistentState({
    key: "filters",
    schema: z.object({
      // The API filters one state at a time: a list of at most one (SingleSelectFilter).
      estado: z.array(schemaFromGuard(isConformityState)).max(1),
    }),
    defaultValue: { estado: [] },
  }),
});
