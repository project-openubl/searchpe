import { createFileRoute } from "@tanstack/react-router";

import { TenantSettings } from "@app/pages/xhandler/tenants/tenant-settings/TenantSettings";

export const Route = createFileRoute(
  "/_authenticated/organizaciones/$tenantId/configuracion",
)({
  component: TenantSettings,
});
