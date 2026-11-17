import { createFileRoute } from "@tanstack/react-router";

import { TenantList } from "@app/pages/xhandler/tenants/tenant-list/TenantList";

export const Route = createFileRoute("/_authenticated/organizaciones/")({
  component: TenantList,
});
