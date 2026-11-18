import { createFileRoute } from "@tanstack/react-router";

import { CompanyCreate } from "@app/pages/xhandler/companies/company-create/CompanyCreate";

export const Route = createFileRoute(
  "/_authenticated/organizaciones/$tenantId/empresas/nueva",
)({
  component: CompanyCreate,
});
