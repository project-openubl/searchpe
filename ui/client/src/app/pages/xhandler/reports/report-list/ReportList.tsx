import type React from "react";
import { useState } from "react";

import { Link } from "@tanstack/react-router";
import dayjs from "dayjs";

import {
  Breadcrumb,
  BreadcrumbItem,
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

import type { ReportView } from "@app/client/xhandler";
import { dataViewBodyStates } from "@app/components/dataViewStates";
import { DocumentTitle } from "@app/components/DocumentTitle";
import { GatedButton } from "@app/components/GatedButton";
import { ListPagination } from "@app/components/ListPagination";
import { TableEmptyState } from "@app/components/TableEmptyState";
import {
  DEFAULT_REFETCH_INTERVAL,
  RENDER_DATETIME_FORMAT,
} from "@app/constants";
import { useNotifications } from "@app/hooks/useNotifications";
import { usePersistentState } from "@app/hooks/usePersistentState";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";
import { useTenantRole } from "@app/hooks/useTenantRole";
import { useSuspenseCompanyDetailQuery } from "@app/queries/xhandler/companies";
import {
  isReportFormat,
  isReportRegister,
  isReportStatus,
  useReportFileDownloadMutation,
  useReportsListQuery,
} from "@app/queries/xhandler/reports";
import { getMutationErrorMessage } from "@app/utils/apiError";
import type { DataViewTh } from "@app/utils/dataViewTypes";
import {
  estimateItemCount,
  PAGINATION_LIST_STATE,
  toPageParams,
} from "@app/utils/listState";

import { CreateReportModal } from "./components/CreateReportModal";
import {
  formatPeriod,
  REPORT_FORMAT_LABELS,
  REPORT_REGISTER_LABELS,
  REPORT_STATUS_PRESENTATION,
} from "./reportPresentation";

interface IReportListProps {
  companyId: string;
}

const formatTime = (value: string | undefined): string =>
  value ? dayjs(value).format(RENDER_DATETIME_FORMAT) : "-";

const statusCell = (status: string | undefined): React.ReactNode => {
  if (!status) return "-";
  if (!isReportStatus(status)) return <Label isCompact>{status}</Label>;
  const { label, color } = REPORT_STATUS_PRESENTATION[status];
  return (
    <Label color={color} isCompact>
      {label}
    </Label>
  );
};

/** A company's sales and purchases reports (DESIGN.md §11.9); each runs as a job. */
export const ReportList: React.FC<IReportListProps> = ({ companyId }) => {
  const tenantId = useSelectedTenantId();
  const { addNotification } = useNotifications();
  const { disabledProps } = useTenantRole();
  const { data: company } = useSuspenseCompanyDetailQuery({ companyId });
  const [isCreateOpen, setIsCreateOpen] = useState(false);

  const [pagination, setPagination] = usePersistentState({
    ...PAGINATION_LIST_STATE.pagination,
    storage: "url",
  });

  const { data, isLoading, error } = useReportsListQuery({
    companyId,
    params: toPageParams(pagination),
    options: {
      // Server-derived status: poll while a report on the page is still being generated.
      refetchInterval: (query) =>
        query.state.data?.items?.some((report) => report.status === "pending")
          ? DEFAULT_REFETCH_INTERVAL
          : false,
    },
  });
  const reports = data?.items ?? [];

  const download = useReportFileDownloadMutation({
    onError: (err) =>
      addNotification({
        variant: "danger",
        ...getMutationErrorMessage({
          error: err,
          fallback: "No se pudo descargar el reporte",
        }),
      }),
  });

  const actionsFor = (report: ReportView): IAction[] => [
    {
      title: "Descargar",
      onClick: () => download.mutate({ companyId, report }),
      isAriaDisabled: report.status !== "done",
      tooltipProps:
        report.status !== "done"
          ? { content: "El reporte se descarga cuando está listo." }
          : undefined,
    },
  ];

  const columns: DataViewTh[] = [
    { cell: "Registro", props: { width: 20 } },
    { cell: "Formato", props: { width: 15 } },
    { cell: "Periodo", props: { width: 10 } },
    { cell: "Estado", props: { width: 10 } },
    { cell: "Filas", props: { width: 10 } },
    { cell: "Solicitado", props: { width: 15 } },
    { cell: "Detalle", props: { width: 20 } },
    { cell: "" },
  ];

  const rows = reports.map((report) => ({
    id: report.id ?? "",
    row: [
      {
        cell:
          report.register && isReportRegister(report.register)
            ? REPORT_REGISTER_LABELS[report.register]
            : (report.register ?? "-"),
      },
      {
        cell:
          report.format && isReportFormat(report.format)
            ? REPORT_FORMAT_LABELS[report.format]
            : (report.format ?? "-"),
        props: { modifier: "truncate" as const },
      },
      { cell: formatPeriod(report.period) },
      { cell: statusCell(report.status) },
      {
        cell:
          report.rows !== undefined && report.rows !== null
            ? report.rows.toLocaleString("es-PE")
            : "-",
      },
      { cell: formatTime(report.created_at) },
      {
        cell: report.detail || "-",
        props: { modifier: "truncate" as const },
      },
      {
        cell: (
          <ActionsColumn
            items={actionsFor(report)}
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
        title="No hay reportes"
        body="Los registros de ventas y compras que solicites aparecerán aquí."
      />
    ),
  });

  const pager = (variant: PaginationVariant) => (
    <ListPagination
      variant={variant}
      itemCount={estimateItemCount({ pagination, pageSize: reports.length })}
      isCountEstimated
      pagination={pagination}
      onPaginationChange={setPagination}
    />
  );

  return (
    <>
      <DocumentTitle title={`Reportes · ${company.ruc ?? ""}`} />
      <PageSection>
        <Breadcrumb>
          <BreadcrumbItem>
            <Link to="/organizaciones/$tenantId/empresas" params={{ tenantId }}>
              Empresas
            </Link>
          </BreadcrumbItem>
          <BreadcrumbItem>
            <Link
              to="/organizaciones/$tenantId/empresas/$companyId"
              params={{ tenantId, companyId }}
            >
              {company.ruc}
            </Link>
          </BreadcrumbItem>
          <BreadcrumbItem isActive>Reportes</BreadcrumbItem>
        </Breadcrumb>
        <Content component={ContentVariants.h1}>Reportes</Content>
        <Content component={ContentVariants.p}>
          Registros de ventas y compras de {company.razon_social || company.ruc}{" "}
          en CSV, Excel, PLE o SIRE.
        </Content>
      </PageSection>
      <PageSection>
        <DataView activeState={activeState}>
          <DataViewToolbar
            actions={
              <ToolbarItem>
                <GatedButton
                  variant="primary"
                  onClick={() => setIsCreateOpen(true)}
                  {...disabledProps("operator")}
                >
                  Nuevo reporte
                </GatedButton>
              </ToolbarItem>
            }
            pagination={pager(PaginationVariant.top)}
          />
          <DataViewTable
            aria-label="Tabla de reportes"
            columns={columns}
            rows={rows}
            bodyStates={bodyStates}
          />
          <DataViewToolbar pagination={pager(PaginationVariant.bottom)} />
        </DataView>
      </PageSection>

      {isCreateOpen ? (
        <CreateReportModal
          companyId={companyId}
          onClose={() => setIsCreateOpen(false)}
        />
      ) : null}
    </>
  );
};
