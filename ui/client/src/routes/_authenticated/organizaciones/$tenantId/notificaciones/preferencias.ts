import { createFileRoute } from "@tanstack/react-router";

import { NotificationPreferences } from "@app/pages/xhandler/notifications/notification-preferences/NotificationPreferences";

export const Route = createFileRoute(
  "/_authenticated/organizaciones/$tenantId/notificaciones/preferencias",
)({
  component: NotificationPreferences,
});
