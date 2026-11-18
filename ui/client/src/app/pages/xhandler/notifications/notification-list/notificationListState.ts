import { z } from "zod";

import { definePersistentState } from "@app/hooks/usePersistentState";
import { defineListState, PAGINATION_STATE } from "@app/utils/listState";

export const NOTIFICATION_LIST_STATE = defineListState({
  pagination: PAGINATION_STATE,
  filters: definePersistentState({
    key: "filters",
    // The API filters unread ones only (`unread=true`); off lists every notification.
    schema: z.object({ noLeidas: z.stringbool() }),
    defaultValue: { noLeidas: false },
  }),
});
