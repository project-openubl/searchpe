import { createFileRoute } from "@tanstack/react-router";

import { DetailRouteError } from "@app/components/DetailRouteError";
import { DetailRoutePending } from "@app/components/DetailRoutePending";
import { ConformityDetailRoute } from "@app/pages/xhandler/conformity/conformity-detail/ConformityDetailRoute";
import { companyDetailQueryOptions } from "@app/queries/xhandler/companies";
import { documentConformityQueryOptions } from "@app/queries/xhandler/conformity";

// `$companyId_`: under the company's address without nesting in its detail page.
export const Route = createFileRoute(
  "/_authenticated/organizaciones/$tenantId/empresas/$companyId_/conformidad/$documentId",
)({
  loader: async ({
    context: { queryClient },
    params: { tenantId, companyId, documentId },
  }) => {
    await Promise.all([
      queryClient.query({
        ...companyDetailQueryOptions({ tenantId, companyId }),
        staleTime: "static",
      }),
      queryClient.query({
        ...documentConformityQueryOptions({ tenantId, documentId }),
        staleTime: "static",
      }),
    ]);
    return { companyId, documentId };
  },
  pendingComponent: () => (
    <DetailRoutePending label="Cargando conformidad de la factura" />
  ),
  errorComponent: DetailRouteError,
  component: ConformityDetailRoute,
});
