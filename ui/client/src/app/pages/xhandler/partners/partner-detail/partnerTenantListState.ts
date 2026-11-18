import dayjs from "dayjs";
import { z } from "zod";

import { definePersistentState } from "@app/hooks/usePersistentState";
import { defineListState, PAGINATION_STATE } from "@app/utils/listState";

/** The months the usage table offers (`AAAA-MM`), newest first: this one and the 11 before. */
export const usageMonths = (now = dayjs()): string[] =>
  Array.from({ length: 12 }, (_, index) =>
    now.subtract(index, "month").format("YYYY-MM"),
  );

export const PARTNER_TENANT_LIST_STATE = defineListState({
  pagination: PAGINATION_STATE,
  filters: definePersistentState({
    key: "filters",
    schema: z.object({
      // One month at a time; none is the current month (the server's default).
      mes: z.array(z.string().regex(/^\d{4}-(0[1-9]|1[0-2])$/)).max(1),
    }),
    defaultValue: { mes: [] },
  }),
});
