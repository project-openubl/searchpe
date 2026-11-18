import { z } from "zod";

import { definePersistentState } from "@app/hooks/usePersistentState";
import { isDeliveryStatus } from "@app/queries/xhandler/webhooks";
import {
  defineListState,
  PAGINATION_STATE,
  schemaFromGuard,
} from "@app/utils/listState";

export const WEBHOOK_DELIVERIES_STATE = defineListState({
  pagination: PAGINATION_STATE,
  filters: definePersistentState({
    key: "filters",
    schema: z.object({
      // The API filters one status at a time (SingleSelectFilter).
      estado: z.array(schemaFromGuard(isDeliveryStatus)).max(1),
    }),
    defaultValue: { estado: [] },
  }),
});
