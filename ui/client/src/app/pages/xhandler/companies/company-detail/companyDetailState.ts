import { z } from "zod";

import { definePersistentState } from "@app/hooks/usePersistentState";
import { defineListState } from "@app/utils/listState";

/** The company page's tabs, in the order shown; the open one is kept in the URL (`pestana`). */
export const COMPANY_DETAIL_TABS = [
  "resumen",
  "configuracion",
  "certificado",
  "series",
  "credenciales",
  "envio",
] as const;

export type CompanyDetailTab = (typeof COMPANY_DETAIL_TABS)[number];

export const isCompanyDetailTab = (value: string): value is CompanyDetailTab =>
  (COMPANY_DETAIL_TABS as readonly string[]).includes(value);

export const COMPANY_DETAIL_STATE = defineListState({
  tab: definePersistentState({
    key: "tab",
    schema: z.object({ pestana: z.enum(COMPANY_DETAIL_TABS) }),
    defaultValue: { pestana: "resumen" },
  }),
});
