import { createFileRoute } from "@tanstack/react-router";

import { useLastTenantStore } from "@app/stores/useLastTenantStore";

// Every tenant page remembers its tenant for the sidebar (ADR 0002).
export const Route = createFileRoute(
  "/_authenticated/organizaciones/$tenantId",
)({
  beforeLoad: ({ params }) => {
    useLastTenantStore.getState().setLastTenantId(params.tenantId);
  },
});
