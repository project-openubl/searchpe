import { createFileRoute } from "@tanstack/react-router";

import { DetailRouteError } from "@app/components/DetailRouteError";
import { DetailRoutePending } from "@app/components/DetailRoutePending";
import { ContribuyenteDetailRoute } from "@app/pages/searchpe/contribuyentes/contribuyente-detail/ContribuyenteDetailRoute";
import { contribuyenteDetailQueryOptions } from "@app/queries/searchpe/contribuyentes";

export const Route = createFileRoute(
  "/_authenticated/padron/contribuyentes/$numeroDocumento",
)({
  loader: async ({ context: { queryClient }, params: { numeroDocumento } }) => {
    await queryClient.query({
      ...contribuyenteDetailQueryOptions({ numeroDocumento }),
      staleTime: "static",
    });
    return { numeroDocumento };
  },
  pendingComponent: () => <DetailRoutePending label="Cargando contribuyente" />,
  errorComponent: DetailRouteError,
  component: ContribuyenteDetailRoute,
});
