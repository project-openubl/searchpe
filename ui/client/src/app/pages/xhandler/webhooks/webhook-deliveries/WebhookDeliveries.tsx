import type React from "react";

import { Link } from "@tanstack/react-router";
import dayjs from "dayjs";

import {
  Breadcrumb,
  BreadcrumbItem,
  Content,
  ContentVariants,
  PageSection,
  PaginationVariant,
} from "@patternfly/react-core";
import {
  DataView,
  DataViewFilters,
  DataViewTable,
  DataViewToolbar,
} from "@patternfly/react-data-view";
import { ActionsColumn, type IAction } from "@patternfly/react-table";

import type { DeliveryView, WebhookView } from "@app/client/xhandler";
import { ClearFiltersButton } from "@app/components/ClearFiltersButton";
import { dataViewBodyStates } from "@app/components/dataViewStates";
import { DocumentTitle } from "@app/components/DocumentTitle";
import { ListPagination } from "@app/components/ListPagination";
import { SingleSelectFilter } from "@app/components/SingleSelectFilter";
import { TableEmptyState } from "@app/components/TableEmptyState";
import {
  DEFAULT_REFETCH_INTERVAL,
  RENDER_DATETIME_FORMAT,
} from "@app/constants";
import { useNotifications } from "@app/hooks/useNotifications";
import { usePersistentState } from "@app/hooks/usePersistentState";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";
import { useTenantRole } from "@app/hooks/useTenantRole";
import {
  DELIVERY_STATUSES,
  useWebhookDeliveriesQuery,
  useWebhookRedeliverMutation,
} from "@app/queries/xhandler/webhooks";
import { getMutationErrorMessage } from "@app/utils/apiError";
import type { DataViewTh } from "@app/utils/dataViewTypes";
import { estimateItemCount, toPageParams } from "@app/utils/listState";

import {
  DELIVERY_STATUS_PRESENTATION,
  DeliveryStatusLabel,
} from "./components/DeliveryStatusLabel";
import { WEBHOOK_DELIVERIES_STATE } from "./webhookDeliveriesState";

const STATUS_OPTIONS = DELIVERY_STATUSES.map((status) => ({
  value: status,
  label: DELIVERY_STATUS_PRESENTATION[status].label,
}));

const formatTime = (value: string | undefined): string =>
  value ? dayjs(value).format(RENDER_DATETIME_FORMAT) : "-";

interface IWebhookDeliveriesProps {
  webhook: WebhookView;
}

/** Every delivery of one webhook, newest first; failed or pending ones can be sent again. */
export const WebhookDeliveries: React.FC<IWebhookDeliveriesProps> = ({
  webhook,
}) => {
  const tenantId = useSelectedTenantId();
  const webhookId = webhook.id ?? "";
  const { addNotification } = useNotifications();
  const { can, disabledProps } = useTenantRole();

  const [pagination, setPagination] = usePersistentState({
    ...WEBHOOK_DELIVERIES_STATE.pagination,
    storage: "url",
  });
  const [filters, setFilters] = usePersistentState({
    ...WEBHOOK_DELIVERIES_STATE.filters,
    storage: "url",
  });
  const toFirstPage = () => setPagination((prev) => ({ ...prev, page: 1 }));
  const clearAllFilters = () => {
    setFilters(WEBHOOK_DELIVERIES_STATE.filters.defaultValue);
    toFirstPage();
  };

  const { data, isLoading, error } = useWebhookDeliveriesQuery({
    webhookId,
    params: { ...toPageParams(pagination), status: filters.estado[0] },
    options: {
      // Server-derived status: poll while a delivery on the page is still pending.
      refetchInterval: (query) =>
        query.state.data?.items?.some((item) => item.status === "pending")
          ? DEFAULT_REFETCH_INTERVAL
          : false,
    },
  });
  const deliveries = data?.items ?? [];

  const redeliver = useWebhookRedeliverMutation(webhookId, {
    onSuccess: (delivery) =>
      addNotification({
        variant: "info",
        title:
          `Se volvió a enviar el evento ${delivery.event_type ?? ""}`.trim(),
      }),
    onError: (err) =>
      addNotification({
        variant: "danger",
        ...getMutationErrorMessage({
          error: err,
          fallback: "No se pudo volver a enviar el evento",
        }),
      }),
  });

  const actionsFor = (delivery: DeliveryView): IAction[] => [
    {
      title: "Volver a enviar",
      onClick: () => redeliver.mutate(delivery),
      isAriaDisabled: !can("operator"),
      tooltipProps: disabledProps("operator").tooltipProps,
    },
  ];

  const columns: DataViewTh[] = [
    { cell: "Evento", props: { width: 20 } },
    { cell: "Estado", props: { width: 10 } },
    { cell: "Intentos", props: { width: 10 } },
    { cell: "Creado", props: { width: 15 } },
    { cell: "Entregado", props: { width: 15 } },
    { cell: "Último error", props: { width: 30 } },
    { cell: "" },
  ];

  const rows = deliveries.map((delivery) => ({
    id: delivery.id ?? "",
    row: [
      {
        cell: delivery.event_type ?? "-",
        props: { modifier: "truncate" as const },
      },
      { cell: <DeliveryStatusLabel status={delivery.status} /> },
      { cell: (delivery.attempts ?? 0).toLocaleString("es-PE") },
      { cell: formatTime(delivery.created_at) },
      { cell: formatTime(delivery.delivered_at) },
      {
        cell:
          [
            delivery.last_status_code
              ? `HTTP ${delivery.last_status_code}`
              : "",
            delivery.last_error ?? "",
          ]
            .filter(Boolean)
            .join(": ") || "-",
        props: { modifier: "truncate" as const },
      },
      {
        cell: (
          <ActionsColumn
            items={actionsFor(delivery)}
            popperProps={{ position: "right" }}
          />
        ),
        props: { isActionCell: true },
      },
    ],
  }));

  const isFiltered = filters.estado.length > 0;

  const { activeState, bodyStates } = dataViewBodyStates({
    columnCount: columns.length,
    isLoading,
    error,
    isEmpty: rows.length === 0,
    emptyState: (
      <TableEmptyState
        title="Este webhook aún no tiene entregas"
        isFiltered={isFiltered}
        onClearFilters={clearAllFilters}
      />
    ),
  });

  const pager = (variant: PaginationVariant) => (
    <ListPagination
      variant={variant}
      itemCount={estimateItemCount({ pagination, pageSize: deliveries.length })}
      isCountEstimated
      pagination={pagination}
      onPaginationChange={setPagination}
    />
  );

  return (
    <>
      <DocumentTitle title="Entregas del webhook" />
      <PageSection>
        <Breadcrumb>
          <BreadcrumbItem>
            <Link to="/organizaciones/$tenantId/webhooks" params={{ tenantId }}>
              Webhooks
            </Link>
          </BreadcrumbItem>
          <BreadcrumbItem isActive>Entregas</BreadcrumbItem>
        </Breadcrumb>
        <Content component={ContentVariants.h1}>Entregas del webhook</Content>
        <Content component={ContentVariants.p}>{webhook.url}</Content>
      </PageSection>
      <PageSection>
        <DataView activeState={activeState}>
          <DataViewToolbar
            clearAllFilters={clearAllFilters}
            customLabelGroupContent={
              <ClearFiltersButton onClick={clearAllFilters} />
            }
            filters={
              <DataViewFilters
                values={filters}
                onChange={(_key, next) => {
                  setFilters((prev) => ({ ...prev, ...next }));
                  toFirstPage();
                }}
              >
                <SingleSelectFilter
                  filterId="estado"
                  title="Estado"
                  placeholder="Filtrar por estado"
                  options={STATUS_OPTIONS}
                />
              </DataViewFilters>
            }
            pagination={pager(PaginationVariant.top)}
          />
          <DataViewTable
            aria-label="Tabla de entregas"
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
