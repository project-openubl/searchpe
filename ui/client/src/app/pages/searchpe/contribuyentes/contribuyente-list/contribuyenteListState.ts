import { z } from "zod";

import { definePersistentState } from "@app/hooks/usePersistentState";
import { isTipoPersona } from "@app/queries/searchpe/contribuyentes";
import {
  defineListState,
  defineSortState,
  PAGINATION_STATE,
  schemaFromGuard,
} from "@app/utils/listState";

/** searchpe sorts contribuyentes by `nombre` only (`ContribuyenteRepository.SORT_BY_FIELDS`). */
export const SORTABLE_COLUMN_KEYS = ["nombre"] as const;

export const CONTRIBUYENTE_LIST_STATE = defineListState({
  pagination: PAGINATION_STATE,
  sort: defineSortState({
    columnKeys: SORTABLE_COLUMN_KEYS,
    defaultValue: { sortBy: "nombre", direction: "asc" },
  }),
  filters: definePersistentState({
    key: "filters",
    schema: z.object({
      texto: z.string(),
      tipoPersona: z.array(schemaFromGuard(isTipoPersona)),
    }),
    defaultValue: { texto: "", tipoPersona: [] },
  }),
});
