import dayjs from "dayjs";
import { z } from "zod";

import { definePersistentState } from "@app/hooks/usePersistentState";
import { isSireBook, isSirePeriod } from "@app/queries/xhandler/sire";
import { defineListState, schemaFromGuard } from "@app/utils/listState";

/** The periods the page offers (`AAAAMM`), newest first: last month and the 23 before. */
export const sirePeriods = (now = dayjs()): string[] =>
  Array.from({ length: 24 }, (_, index) =>
    now.subtract(index + 1, "month").format("YYYYMM"),
  );

/** The register and period on screen, kept in the URL so a link opens the same period. */
export const SIRE_PERIOD_STATE = defineListState({
  period: definePersistentState({
    key: "period",
    schema: z.object({
      libro: schemaFromGuard(isSireBook),
      periodo: z.string().refine(isSirePeriod),
    }),
    // Last month: the one being declared.
    defaultValue: { libro: "rvie", periodo: sirePeriods()[0] },
  }),
});
