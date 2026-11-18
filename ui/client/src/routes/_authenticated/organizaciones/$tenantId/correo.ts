import { createFileRoute } from "@tanstack/react-router";

import { EmailSettings } from "@app/pages/xhandler/email/email-settings/EmailSettings";

// Its two lists are prefixed (`dominios.page`, `supresiones.page`), so the route declares none.
export const Route = createFileRoute(
  "/_authenticated/organizaciones/$tenantId/correo",
)({
  component: EmailSettings,
});
