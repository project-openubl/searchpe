import { createFileRoute } from "@tanstack/react-router";

import { DetailRouteError } from "@app/components/DetailRouteError";
import { DetailRoutePending } from "@app/components/DetailRoutePending";
import { SirePeriodListRoute } from "@app/pages/xhandler/sire/sire-period-list/SirePeriodListRoute";
import { SIRE_PERIOD_LIST_STATE } from "@app/pages/xhandler/sire/sire-period-list/sirePeriodListState";
import { companyDetailQueryOptions } from "@app/queries/xhandler/companies";
import { listSearch } from "@app/utils/listState";

const { validateSearch, search } = listSearch(SIRE_PERIOD_LIST_STATE);

// `$companyId_` and `sire_`: under the SIRE page's address without nesting in it.
export const Route = createFileRoute(
  "/_authenticated/organizaciones/$tenantId/empresas/$companyId_/sire_/periodos",
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
  component: SirePeriodListRoute,
});
