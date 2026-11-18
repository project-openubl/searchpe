import { createFileRoute } from "@tanstack/react-router";

import { DetailRouteError } from "@app/components/DetailRouteError";
import { DetailRoutePending } from "@app/components/DetailRoutePending";
import { PseAuthorizationRoute } from "@app/pages/xhandler/pse/pse-authorization/PseAuthorizationRoute";
import { companyDetailQueryOptions } from "@app/queries/xhandler/companies";

// `$companyId_`: under the company's address without nesting in its detail page.
export const Route = createFileRoute(
  "/_authenticated/organizaciones/$tenantId/empresas/$companyId_/pse",
)({
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
  component: PseAuthorizationRoute,
});
