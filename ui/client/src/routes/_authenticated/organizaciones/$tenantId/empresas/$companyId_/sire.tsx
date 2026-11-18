import { createFileRoute } from "@tanstack/react-router";

import { DetailRouteError } from "@app/components/DetailRouteError";
import { DetailRoutePending } from "@app/components/DetailRoutePending";
import { SirePeriodRoute } from "@app/pages/xhandler/sire/sire-period/SirePeriodRoute";
import { SIRE_PERIOD_STATE } from "@app/pages/xhandler/sire/sire-period/sirePeriodState";
import { companyDetailQueryOptions } from "@app/queries/xhandler/companies";
import { listSearch } from "@app/utils/listState";

const { validateSearch, search } = listSearch(SIRE_PERIOD_STATE);

// `$companyId_`: under the company's address without nesting in its detail page.
export const Route = createFileRoute(
  "/_authenticated/organizaciones/$tenantId/empresas/$companyId_/sire",
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
  component: SirePeriodRoute,
});
