import type React from "react";

import { Link } from "@tanstack/react-router";
import dayjs from "dayjs";

import {
  Button,
  Content,
  ContentVariants,
  Flex,
  FlexItem,
  Label,
  PageSection,
  PaginationVariant,
  Switch,
  ToolbarItem,
} from "@patternfly/react-core";
import {
  DataView,
  DataViewTable,
  DataViewToolbar,
} from "@patternfly/react-data-view";
import { ActionsColumn } from "@patternfly/react-table";

import type { NotificationView } from "@app/client/xhandler";
import { dataViewBodyStates } from "@app/components/dataViewStates";
import { DocumentTitle } from "@app/components/DocumentTitle";
import { ListPagination } from "@app/components/ListPagination";
import { TableEmptyState } from "@app/components/TableEmptyState";
import { TenantContextSelector } from "@app/components/TenantContextSelector";
import { RENDER_DATETIME_FORMAT } from "@app/constants";
import { useNotifications } from "@app/hooks/useNotifications";
import { usePersistentState } from "@app/hooks/usePersistentState";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";
import {
  useNotificationReadMutation,
  useNotificationsListQuery,
  useNotificationsReadAllMutation,
} from "@app/queries/xhandler/notifications";
import { getMutationErrorMessage } from "@app/utils/apiError";
import type { DataViewTh } from "@app/utils/dataViewTypes";
import { estimateItemCount, toPageParams } from "@app/utils/listState";
import { notificationKindLabel } from "@app/utils/notificationKinds";

import { NOTIFICATION_LIST_STATE } from "./notificationListState";

/** The organization's in-app notifications, newest first (DESIGN.md §26.6). */
export const NotificationList: React.FC = () => {
  const tenantId = useSelectedTenantId();
  const { addNotification } = useNotifications();
  const [pagination, setPagination] = usePersistentState({
    ...NOTIFICATION_LIST_STATE.pagination,
    storage: "url",
  });
  const [filters, setFilters] = usePersistentState({
    ...NOTIFICATION_LIST_STATE.filters,
    storage: "url",
  });

  const { data, isLoading, error } = useNotificationsListQuery({
    params: {
      ...toPageParams(pagination),
      unread: filters.noLeidas || undefined,
    },
  });
  const notifications = data?.items ?? [];

  const markRead = useNotificationReadMutation({
    onError: (err) =>
      addNotification({
        variant: "danger",
        ...getMutationErrorMessage({
          error: err,
          fallback: "No se pudo marcar la notificación como leída",
        }),
      }),
  });
  const markAllRead = useNotificationsReadAllMutation({
    onSuccess: () =>
      addNotification({
        variant: "success",
        title: "Se marcaron todas las notificaciones como leídas",
      }),
    onError: (err) =>
      addNotification({
        variant: "danger",
        ...getMutationErrorMessage({
          error: err,
          fallback: "No se pudieron marcar las notificaciones como leídas",
        }),
      }),
  });

  const columns: DataViewTh[] = [
    { cell: "Notificación", props: { width: 45 } },
    { cell: "Tipo", props: { width: 25 } },
    { cell: "Fecha", props: { width: 20 } },
    { cell: "Estado", props: { width: 10 } },
    { cell: "" },
  ];

  const rowOf = (notification: NotificationView) => ({
    id: notification.id ?? "",
    row: [
      {
        cell: (
          <>
            <Content component={ContentVariants.p}>
              {notification.read_at ? (
                notification.title
              ) : (
                <strong>{notification.title}</strong>
              )}
            </Content>
            {notification.body ? (
              <Content component={ContentVariants.small}>
                {notification.body}
              </Content>
            ) : null}
          </>
        ),
      },
      { cell: notificationKindLabel(notification.kind) },
      {
        cell: notification.created_at
          ? dayjs(notification.created_at).format(RENDER_DATETIME_FORMAT)
          : "-",
      },
      {
        cell: notification.read_at ? (
          <Label isCompact>Leída</Label>
        ) : (
          <Label color="blue" isCompact>
            Nueva
          </Label>
        ),
      },
      {
        cell: (
          <ActionsColumn
            items={[
              {
                title: "Marcar como leída",
                onClick: () => markRead.mutate(notification),
                isAriaDisabled: !!notification.read_at,
                tooltipProps: notification.read_at
                  ? { content: "Ya está leída." }
                  : undefined,
              },
            ]}
            popperProps={{ position: "right" }}
          />
        ),
        props: { isActionCell: true },
      },
    ],
  });
  const rows = notifications.map(rowOf);

  const clearFilters = () => {
    setFilters(NOTIFICATION_LIST_STATE.filters.defaultValue);
    setPagination((prev) => ({ ...prev, page: 1 }));
  };

  const { activeState, bodyStates } = dataViewBodyStates({
    columnCount: columns.length,
    isLoading,
    error,
    isEmpty: rows.length === 0,
    emptyState: (
      <TableEmptyState
        title="No hay notificaciones"
        isFiltered={filters.noLeidas}
        filteredTitle="No hay notificaciones sin leer"
        filteredBody="Todas las notificaciones están leídas."
        onClearFilters={clearFilters}
      />
    ),
  });

  const pager = (variant: PaginationVariant) => (
    <ListPagination
      variant={variant}
      itemCount={estimateItemCount({
        pagination,
        pageSize: notifications.length,
      })}
      isCountEstimated
      pagination={pagination}
      onPaginationChange={setPagination}
    />
  );

  return (
    <>
      <DocumentTitle title="Notificaciones" />
      <TenantContextSelector />
      <PageSection>
        <Flex
          justifyContent={{ default: "justifyContentSpaceBetween" }}
          alignItems={{ default: "alignItemsCenter" }}
        >
          <FlexItem>
            <Content component={ContentVariants.h1}>Notificaciones</Content>
            <Content component={ContentVariants.p}>
              Los avisos de esta organización. Elige cuáles recibir en{" "}
              <Link
                to="/organizaciones/$tenantId/notificaciones/preferencias"
                params={{ tenantId }}
              >
                Preferencias
              </Link>
              .
            </Content>
          </FlexItem>
          <FlexItem>
            <Button
              variant="secondary"
              onClick={() => markAllRead.mutate()}
              isLoading={markAllRead.isPending}
              isDisabled={markAllRead.isPending}
            >
              Marcar todas como leídas
            </Button>
          </FlexItem>
        </Flex>
      </PageSection>
      <PageSection>
        <DataView activeState={activeState}>
          <DataViewToolbar
            filters={
              <ToolbarItem>
                <Switch
                  id="notifications-unread-only"
                  label="Solo no leídas"
                  isChecked={filters.noLeidas}
                  onChange={(_event, checked) => {
                    setFilters({ noLeidas: checked });
                    setPagination((prev) => ({ ...prev, page: 1 }));
                  }}
                />
              </ToolbarItem>
            }
            pagination={pager(PaginationVariant.top)}
          />
          <DataViewTable
            aria-label="Tabla de notificaciones"
            columns={columns}
            rows={rows}
            bodyStates={bodyStates}
          />
          <DataViewToolbar pagination={pager(PaginationVariant.bottom)} />
        </DataView>
      </PageSection>
    </>
  );
};
