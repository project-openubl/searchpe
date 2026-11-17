import { z } from "zod";

import { definePersistentState } from "@app/hooks/usePersistentState";
import { isJobState } from "@app/queries/xhandler/jobs";
import {
  defineListState,
  PAGINATION_STATE,
  schemaFromGuard,
} from "@app/utils/listState";

export const JOB_LIST_STATE = defineListState({
  pagination: PAGINATION_STATE,
  filters: definePersistentState({
    key: "filters",
    schema: z.object({
      // The API filters one state at a time.
      estado: z.array(schemaFromGuard(isJobState)),
      nombre: z.string(),
    }),
    defaultValue: { estado: [], nombre: "" },
  }),
});
