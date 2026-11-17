import { createFileRoute } from "@tanstack/react-router";

import { CompanyList } from "@app/pages/xhandler/companies/company-list/CompanyList";
import { listSearch, PAGINATION_LIST_STATE } from "@app/utils/listState";

const { validateSearch, search } = listSearch(PAGINATION_LIST_STATE);

export const Route = createFileRoute(
  "/_authenticated/organizaciones/$tenantId/empresas/",
)({
  validateSearch,
  search,
  component: CompanyList,
});
