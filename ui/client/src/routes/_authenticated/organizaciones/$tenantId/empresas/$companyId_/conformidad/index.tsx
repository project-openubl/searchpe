import { createFileRoute } from "@tanstack/react-router";

import { DetailRouteError } from "@app/components/DetailRouteError";
import { DetailRoutePending } from "@app/components/DetailRoutePending";
import { ConformityListRoute } from "@app/pages/xhandler/conformity/conformity-list/ConformityListRoute";
import { CONFORMITY_LIST_STATE } from "@app/pages/xhandler/conformity/conformity-list/conformityListState";
import { companyDetailQueryOptions } from "@app/queries/xhandler/companies";
import { listSearch } from "@app/utils/listState";

const { validateSearch, search } = listSearch(CONFORMITY_LIST_STATE);

// `$companyId_`: under the company's address without nesting in its detail page.
export const Route = createFileRoute(
  "/_authenticated/organizaciones/$tenantId/empresas/$companyId_/conformidad/",
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
  component: ConformityListRoute,
});
