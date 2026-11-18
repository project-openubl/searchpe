import { createFileRoute } from "@tanstack/react-router";

import { NotificationList } from "@app/pages/xhandler/notifications/notification-list/NotificationList";
import { NOTIFICATION_LIST_STATE } from "@app/pages/xhandler/notifications/notification-list/notificationListState";
import { listSearch } from "@app/utils/listState";

const { validateSearch, search } = listSearch(NOTIFICATION_LIST_STATE);

export const Route = createFileRoute(
  "/_authenticated/organizaciones/$tenantId/notificaciones/",
)({
  validateSearch,
  search,
  component: NotificationList,
});
