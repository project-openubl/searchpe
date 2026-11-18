import type React from "react";
import { useState } from "react";

import { useNavigate } from "@tanstack/react-router";
import dayjs from "dayjs";

import {
  Card,
  CardBody,
  CardFooter,
  CardTitle,
  Content,
  ContentVariants,
  Label,
  PageSection,
  PaginationVariant,
  ToolbarItem,
} from "@patternfly/react-core";
import {
  DataView,
  DataViewTable,
  DataViewToolbar,
} from "@patternfly/react-data-view";
import { ActionsColumn, type IAction } from "@patternfly/react-table";

import type { ExportView } from "@app/client/xhandler";
import { ConfirmActionModal } from "@app/components/ConfirmActionModal";
import { dataViewBodyStates } from "@app/components/dataViewStates";
import { DocumentTitle } from "@app/components/DocumentTitle";
import { GatedButton } from "@app/components/GatedButton";
import { ListPagination } from "@app/components/ListPagination";
import { TableEmptyState } from "@app/components/TableEmptyState";
import { TenantContextSelector } from "@app/components/TenantContextSelector";
import { UnauthorizedState } from "@app/components/UnauthorizedState";
import {
  DEFAULT_REFETCH_INTERVAL,
  RENDER_DATETIME_FORMAT,
} from "@app/constants";
import { useNotifications } from "@app/hooks/useNotifications";
import { usePersistentState } from "@app/hooks/usePersistentState";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";
import { useTenantRole } from "@app/hooks/useTenantRole";
import {
  exportParts,
  isExportStatus,
  useExportCreateMutation,
  useExportPartDownloadMutation,
  useExportsListQuery,
  useTenantExitMutation,
} from "@app/queries/xhandler/exports";
import { useMyTenantsQuery } from "@app/queries/xhandler/tenants";
import { getMutationErrorMessage } from "@app/utils/apiError";
import type { DataViewTh } from "@app/utils/dataViewTypes";
import { PAGINATION_LIST_STATE, toPageParams } from "@app/utils/listState";

import { EXPORT_STATUS_PRESENTATION, formatBytes } from "./exportPresentation";

const formatTime = (value: string | undefined): string =>
  value ? dayjs(value).format(RENDER_DATETIME_FORMAT) : "-";

const statusCell = (status: string | undefined): React.ReactNode => {
  if (!status) return "-";
  if (!isExportStatus(status)) return <Label isCompact>{status}</Label>;
  const { label, color } = EXPORT_STATUS_PRESENTATION[status];
  return (
    <Label color={color} isCompact>
      {label}
    </Label>
  );
};

/**
 * The tenant's data exports (every document's XML, CDR, PDF and JSON, plus receptions) and
 * leaving the platform, which needs a recent export (DESIGN.md §13).
 */
export const ExportList: React.FC = () => {
  const tenantId = useSelectedTenantId();
  const navigate = useNavigate();
  const { addNotification } = useNotifications();
  const { can, disabledProps } = useTenantRole();
  const { data: tenants } = useMyTenantsQuery({});
  const tenantName = tenants?.find((tenant) => tenant.id === tenantId)?.name;
  const [isExitOpen, setIsExitOpen] = useState(false);

  const [pagination, setPagination] = usePersistentState({
    ...PAGINATION_LIST_STATE.pagination,
    storage: "url",
  });

  const isAdmin = can("admin");
  // GET /exports is unpaged (the latest 20): read whole, paged on screen.
  const { data, isLoading, error } = useExportsListQuery({
    options: {
      enabled: isAdmin,
      // Server-derived status: poll while an export is being built.
      refetchInterval: (query) =>
        query.state.data?.some((item) => item.status === "pending")
          ? DEFAULT_REFETCH_INTERVAL
          : false,
    },
  });
  const exports = data ?? [];
  const { limit, offset } = toPageParams(pagination);
  const pageExports = exports.slice(offset, offset + limit);

  const errorToast = (fallback: string) => (err: unknown) =>
    addNotification({
      variant: "danger",
      ...getMutationErrorMessage({ error: err, fallback }),
    });

  const createExport = useExportCreateMutation({
    onSuccess: () =>
      addNotification({
        variant: "info",
        title: "Se solicitó la exportación",
        description:
          "Un trabajo la genera; podrás descargar sus partes cuando esté lista.",
      }),
    onError: errorToast("No se pudo solicitar la exportación"),
  });
  const downloadPart = useExportPartDownloadMutation({
    onError: errorToast("No se pudo descargar la parte"),
  });
  const exitTenant = useTenantExitMutation({
    onSuccess: async (_data, { confirm_name }) => {
      addNotification({
        variant: "success",
        title: `La organización ${confirm_name} dejó la plataforma`,
        description:
          "Sus datos se conservan el plazo legal y luego se eliminan.",
      });
      await navigate({ to: "/organizaciones" });
    },
    onError: errorToast("No se pudo dejar la plataforma"),
  });

  const actionsFor = (item: ExportView): IAction[] =>
    item.status === "done"
      ? exportParts(item).map((part) => ({
          title: `Descargar ${part.name}${part.size !== undefined ? ` (${formatBytes(part.size)})` : ""}`,
          onClick: () => downloadPart.mutate({ exportId: item.id ?? "", part }),
        }))
      : [
          {
            title: "Descargar",
            isAriaDisabled: true,
            tooltipProps: {
              content:
                "Las partes se descargan cuando la exportación está lista.",
            },
          },
        ];

  const columns: DataViewTh[] = [
    { cell: "Solicitada", props: { width: 20 } },
    { cell: "Estado", props: { width: 15 } },
    { cell: "Comprobantes", props: { width: 15 } },
    { cell: "Partes", props: { width: 10 } },
    { cell: "Finalizada", props: { width: 20 } },
    { cell: "Detalle", props: { width: 20 } },
    { cell: "" },
  ];

  const rows = pageExports.map((item) => ({
    id: item.id ?? "",
    row: [
      { cell: formatTime(item.created_at) },
      { cell: statusCell(item.status) },
      {
        cell:
          item.documents !== undefined && item.documents !== null
            ? item.documents.toLocaleString("es-PE")
            : "-",
      },
      { cell: item.parts?.length ? String(item.parts.length) : "-" },
      { cell: formatTime(item.done_at) },
      { cell: item.detail || "-", props: { modifier: "truncate" as const } },
      {
        cell: (
          <ActionsColumn
            items={actionsFor(item)}
            popperProps={{ position: "right" }}
          />
        ),
        props: { isActionCell: true },
      },
    ],
  }));

  const { activeState, bodyStates } = dataViewBodyStates({
    columnCount: columns.length,
    isLoading,
    error,
    isEmpty: rows.length === 0,
    emptyState: (
      <TableEmptyState
        title="No hay exportaciones"
        body="Una exportación reúne los archivos de todos los comprobantes de la organización."
      />
    ),
  });

  const pager = (variant: PaginationVariant) => (
    <ListPagination
      variant={variant}
      itemCount={exports.length}
      pagination={pagination}
      onPaginationChange={setPagination}
    />
  );

  return (
    <>
      <DocumentTitle title="Exportaciones" />
      <TenantContextSelector />
      <PageSection>
        <Content component={ContentVariants.h1}>Exportaciones</Content>
        <Content component={ContentVariants.p}>
          Descarga todos los comprobantes de la organización (XML, CDR, PDF y
          JSON) y las recepciones, en archivos ZIP.
        </Content>
      </PageSection>
      <PageSection>
        {isAdmin ? (
          <DataView activeState={activeState}>
            <DataViewToolbar
              actions={
                <ToolbarItem>
                  <GatedButton
                    variant="primary"
                    isLoading={createExport.isPending}
                    onClick={() => createExport.mutate(crypto.randomUUID())}
                  >
                    Nueva exportación
                  </GatedButton>
                </ToolbarItem>
              }
              pagination={pager(PaginationVariant.top)}
            />
            <DataViewTable
              aria-label="Tabla de exportaciones"
              columns={columns}
              rows={rows}
              bodyStates={bodyStates}
            />
            <DataViewToolbar pagination={pager(PaginationVariant.bottom)} />
          </DataView>
        ) : (
          <UnauthorizedState description="Necesitas el rol Administrador o superior para exportar los datos de la organización." />
        )}
      </PageSection>
      <PageSection>
        <Card>
          <CardTitle>Dejar la plataforma</CardTitle>
          <CardBody>
            La organización deja de emitir y sus datos se eliminan al vencer el
            plazo legal de conservación. Antes necesitas una exportación de los
            últimos 30 días que incluya todos los comprobantes, y ninguno puede
            estar esperando respuesta de SUNAT.
          </CardBody>
          <CardFooter>
            <GatedButton
              variant="secondary"
              onClick={() => setIsExitOpen(true)}
              {...disabledProps("owner")}
            >
              Dejar la plataforma
            </GatedButton>
          </CardFooter>
        </Card>
      </PageSection>

      <ConfirmActionModal
        isOpen={isExitOpen}
        title="¿Dejar la plataforma?"
        body="La organización dejará de emitir comprobantes. No se puede deshacer."
        confirmLabel="Dejar la plataforma"
        cancelLabel="Volver"
        titleIconVariant="warning"
        confirmText={tenantName}
        isConfirming={exitTenant.isPending}
        onConfirm={() =>
          tenantName &&
          exitTenant.mutate(
            { confirm_name: tenantName },
            { onSuccess: () => setIsExitOpen(false) },
          )
        }
        onCancel={() => setIsExitOpen(false)}
      />
    </>
  );
};
