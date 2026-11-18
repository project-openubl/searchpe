import type { UseQueryOptions } from "@tanstack/react-query";
import {
  keepPreviousData,
  queryOptions,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import {
  getNotificationPreferences,
  getNotificationSettings,
  listNotifications,
  type ListNotificationsData,
  markAllNotificationsRead,
  markNotificationRead,
  type NotificationSettings,
  type NotificationView,
  type PageNotificationView,
  type Preference,
  setNotificationPreferences,
  updateNotificationSettings,
} from "@app/client/xhandler";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";

import type { ListParams } from "../utils/listParams";
import type { IMutationCallbacks } from "../utils/mutationCallbacks";
import type { XhandlerAxiosError } from "../utils/openapiErrorTypes";

export const NotificationsQueryKey = "notifications";

/** Notification kinds (DESIGN.md §26.6, `Notifications`); `privacy.*` ones are mandatory. */
export const NOTIFICATION_KINDS = [
  "document.exception",
  "document.at_risk",
  "document.rejected_after_delivery",
  "certificate.expiring",
  "sunat.outage",
  "quota",
  "pse.authorization_revoked",
  "conformity.objected",
  "sire.deadline",
] as const;

export type NotificationKind = (typeof NOTIFICATION_KINDS)[number];

export type INotificationListParams = ListParams<ListNotificationsData>;

/**
 * Every known kind with its stored preference, or the default (email and in-app on) when the
 * tenant never set one: `GET …/notification-preferences` answers only the stored rows.
 */
export const withDefaultPreferences = (stored: Preference[]): Preference[] => {
  const byKind = new Map(stored.map((pref) => [pref.kind, pref]));
  const known = NOTIFICATION_KINDS.map(
    (kind) => byKind.get(kind) ?? { kind, email: true, in_app: true },
  );
  const unknown = stored.filter(
    (pref) => !(NOTIFICATION_KINDS as readonly string[]).includes(pref.kind),
  );
  return [...known, ...unknown];
};

export const notificationKeys = {
  all: () => [NotificationsQueryKey] as const,
  list: (tenantId: string) =>
    [...notificationKeys.all(), tenantId, "list"] as const,
  preferences: (tenantId: string) =>
    [...notificationKeys.all(), tenantId, "preferences"] as const,
  settings: (tenantId: string) =>
    [...notificationKeys.all(), tenantId, "settings"] as const,

  listQuery: (tenantId: string, params: INotificationListParams) =>
    [...notificationKeys.list(tenantId), params] as const,
};

type NotificationListOptions = Omit<
  UseQueryOptions<PageNotificationView, XhandlerAxiosError>,
  "queryKey" | "queryFn"
>;

export const notificationsListQueryOptions = ({
  tenantId,
  params,
  options,
}: {
  tenantId: string;
  params: INotificationListParams;
  options?: NotificationListOptions;
}) =>
  queryOptions<PageNotificationView, XhandlerAxiosError>({
    placeholderData: keepPreviousData,
    ...options,
    queryKey: notificationKeys.listQuery(tenantId, params),
    queryFn: async () =>
      (
        await listNotifications({
          path: { tenantId },
          query: params,
          throwOnError: true,
        })
      ).data,
  });

/** `GET …/notifications`: the tenant's in-app notifications, newest first. */
export const useNotificationsListQuery = ({
  params,
  options,
}: {
  params: INotificationListParams;
  options?: NotificationListOptions;
}) => {
  const tenantId = useSelectedTenantId();
  return useQuery(notificationsListQueryOptions({ tenantId, params, options }));
};

/** `POST …/notifications/{id}/read` (viewer). */
export const useNotificationReadMutation = (
  options?: IMutationCallbacks<void, NotificationView, XhandlerAxiosError>,
) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return useMutation<void, XhandlerAxiosError, NotificationView>({
    mutationFn: async (notification) => {
      await markNotificationRead({
        path: { tenantId, id: notification.id ?? "" },
        throwOnError: true,
      });
    },
    onSuccess: async (data, notification) => {
      await queryClient.invalidateQueries({
        queryKey: notificationKeys.list(tenantId),
      });
      await options?.onSuccess?.(data, notification);
    },
    onError: options?.onError,
  });
};

/** `POST …/notifications/read-all` (viewer). */
export const useNotificationsReadAllMutation = (
  options?: IMutationCallbacks<void, void, XhandlerAxiosError>,
) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return useMutation<void, XhandlerAxiosError, void>({
    mutationFn: async () => {
      await markAllNotificationsRead({
        path: { tenantId },
        throwOnError: true,
      });
    },
    onSuccess: async (data, variables) => {
      await queryClient.invalidateQueries({
        queryKey: notificationKeys.list(tenantId),
      });
      await options?.onSuccess?.(data, variables);
    },
    onError: options?.onError,
  });
};

export const notificationPreferencesQueryOptions = ({
  tenantId,
}: {
  tenantId: string;
}) =>
  queryOptions<Preference[], XhandlerAxiosError>({
    queryKey: notificationKeys.preferences(tenantId),
    queryFn: async () =>
      (
        await getNotificationPreferences({
          path: { tenantId },
          throwOnError: true,
        })
      ).data,
  });

/** `GET …/notification-preferences`: the stored rows only (see `withDefaultPreferences`). */
export const useNotificationPreferencesQuery = () => {
  const tenantId = useSelectedTenantId();
  return useQuery({
    ...notificationPreferencesQueryOptions({ tenantId }),
    enabled: !!tenantId,
  });
};

/** `PUT …/notification-preferences` (admin): upserts the given kinds, answers every stored row. */
export const useNotificationPreferencesMutation = (
  options?: IMutationCallbacks<Preference[], Preference[], XhandlerAxiosError>,
) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return useMutation<Preference[], XhandlerAxiosError, Preference[]>({
    mutationFn: async (body) =>
      (
        await setNotificationPreferences({
          path: { tenantId },
          body,
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, variables) => {
      queryClient.setQueryData(notificationKeys.preferences(tenantId), data);
      await options?.onSuccess?.(data, variables);
    },
    onError: options?.onError,
  });
};

export const notificationSettingsQueryOptions = ({
  tenantId,
}: {
  tenantId: string;
}) =>
  queryOptions<NotificationSettings, XhandlerAxiosError>({
    queryKey: notificationKeys.settings(tenantId),
    queryFn: async () =>
      (
        await getNotificationSettings({
          path: { tenantId },
          throwOnError: true,
        })
      ).data,
  });

/** `GET …/notification-settings` (viewer): the tenant's notification address, if any. */
export const useNotificationSettingsQuery = () => {
  const tenantId = useSelectedTenantId();
  return useQuery({
    ...notificationSettingsQueryOptions({ tenantId }),
    enabled: !!tenantId,
  });
};

/** `PUT …/notification-settings` (admin): the tenant's notification address; blank clears it. */
export const useNotificationSettingsMutation = (
  options?: IMutationCallbacks<
    NotificationSettings,
    NotificationSettings,
    XhandlerAxiosError
  >,
) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return useMutation<
    NotificationSettings,
    XhandlerAxiosError,
    NotificationSettings
  >({
    mutationFn: async (body) =>
      (
        await updateNotificationSettings({
          path: { tenantId },
          body,
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, variables) => {
      queryClient.setQueryData(notificationKeys.settings(tenantId), data);
      await options?.onSuccess?.(data, variables);
    },
    onError: options?.onError,
  });
};
