import { createFileRoute } from "@tanstack/react-router";

import { DetailRouteError } from "@app/components/DetailRouteError";
import { DetailRoutePending } from "@app/components/DetailRoutePending";
import { PartnerDetailRoute } from "@app/pages/xhandler/partners/partner-detail/PartnerDetailRoute";
import { PARTNER_TENANT_LIST_STATE } from "@app/pages/xhandler/partners/partner-detail/partnerTenantListState";
import { partnerTenantsQueryOptions } from "@app/queries/xhandler/partners";
import { listSearch } from "@app/utils/listState";

const { validateSearch, search } = listSearch(PARTNER_TENANT_LIST_STATE);

export const Route = createFileRoute("/_authenticated/socios/$partnerId")({
  validateSearch,
  search,
  // The partner API has no read of one partner: its tenants answer 404 for an unknown one.
  loader: async ({ context: { queryClient }, params: { partnerId } }) => {
    await queryClient.query({
      ...partnerTenantsQueryOptions({ partnerId, params: {} }),
      staleTime: "static",
    });
    return { partnerId };
  },
  pendingComponent: () => <DetailRoutePending label="Cargando socio" />,
  errorComponent: DetailRouteError,
  component: PartnerDetailRoute,
});
