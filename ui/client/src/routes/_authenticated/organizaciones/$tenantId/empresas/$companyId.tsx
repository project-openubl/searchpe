import { createFileRoute } from "@tanstack/react-router";

import { DetailRouteError } from "@app/components/DetailRouteError";
import { DetailRoutePending } from "@app/components/DetailRoutePending";
import { CompanyDetailRoute } from "@app/pages/xhandler/companies/company-detail/CompanyDetailRoute";
import { companyDetailQueryOptions } from "@app/queries/xhandler/companies";

export const Route = createFileRoute(
  "/_authenticated/organizaciones/$tenantId/empresas/$companyId",
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
  component: CompanyDetailRoute,
});
