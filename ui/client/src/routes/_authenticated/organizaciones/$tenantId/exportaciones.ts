import { createFileRoute } from "@tanstack/react-router";

import { ExportList } from "@app/pages/xhandler/exports/export-list/ExportList";
import { listSearch, PAGINATION_LIST_STATE } from "@app/utils/listState";

const { validateSearch, search } = listSearch(PAGINATION_LIST_STATE);

export const Route = createFileRoute(
  "/_authenticated/organizaciones/$tenantId/exportaciones",
)({
  validateSearch,
  search,
  component: ExportList,
});
