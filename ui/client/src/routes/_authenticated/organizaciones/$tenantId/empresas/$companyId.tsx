import { createFileRoute } from "@tanstack/react-router";

import { DetailRouteError } from "@app/components/DetailRouteError";
import { DetailRoutePending } from "@app/components/DetailRoutePending";
import { CompanyDetailRoute } from "@app/pages/xhandler/companies/company-detail/CompanyDetailRoute";
import { COMPANY_DETAIL_STATE } from "@app/pages/xhandler/companies/company-detail/companyDetailState";
import { companyDetailQueryOptions } from "@app/queries/xhandler/companies";
import { listSearch } from "@app/utils/listState";

// The open tab is kept in the URL (`?pestana=`), like a list's state.
const { validateSearch, search } = listSearch(COMPANY_DETAIL_STATE);

export const Route = createFileRoute(
  "/_authenticated/organizaciones/$tenantId/empresas/$companyId",
)({
  validateSearch,
  search,
  loader: async ({
    context: { queryClient },
    params: { tenantId, companyId },
  }) => {
    await queryClient.query({
      ...companyDetailQueryOptions({ tenantId, companyId }),
      staleTime: "static",
    });
    return { companyId };
  },
  pendingComponent: () => <DetailRoutePending label="Cargando empresa" />,
  errorComponent: DetailRouteError,
  component: CompanyDetailRoute,
});
