import { createFileRoute } from "@tanstack/react-router";

import { ContribuyenteList } from "@app/pages/searchpe/contribuyentes/contribuyente-list/ContribuyenteList";
import { CONTRIBUYENTE_LIST_STATE } from "@app/pages/searchpe/contribuyentes/contribuyente-list/contribuyenteListState";
import { listSearch } from "@app/utils/listState";

const { validateSearch, search } = listSearch(CONTRIBUYENTE_LIST_STATE);

export const Route = createFileRoute("/_authenticated/padron/contribuyentes/")({
  validateSearch,
  search,
  component: ContribuyenteList,
});
