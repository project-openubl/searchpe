import { createFileRoute } from "@tanstack/react-router";

import { DataSubjectRequestList } from "@app/pages/xhandler/data-subject-requests/data-subject-request-list/DataSubjectRequestList";
import { listSearch, PAGINATION_LIST_STATE } from "@app/utils/listState";

const { validateSearch, search } = listSearch(PAGINATION_LIST_STATE);

export const Route = createFileRoute(
  "/_authenticated/organizaciones/$tenantId/datos-personales",
)({
  validateSearch,
  search,
  component: DataSubjectRequestList,
});
