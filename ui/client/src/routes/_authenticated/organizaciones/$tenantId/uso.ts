import { createFileRoute } from "@tanstack/react-router";

import { BillingUsage } from "@app/pages/xhandler/billing/billing-usage/BillingUsage";

export const Route = createFileRoute(
  "/_authenticated/organizaciones/$tenantId/uso",
)({
  component: BillingUsage,
});
