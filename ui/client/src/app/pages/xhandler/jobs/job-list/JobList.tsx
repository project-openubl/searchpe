import type React from "react";
import { useState } from "react";

import dayjs from "dayjs";

import {
  Content,
  ContentVariants,
  PageSection,
  PaginationVariant,
} from "@patternfly/react-core";
import {
  DataView,
  DataViewFilters,
  DataViewTable,
  DataViewTextFilter,
  DataViewToolbar,
} from "@patternfly/react-data-view";
import { ActionsColumn, type IAction } from "@patternfly/react-table";

import type { JobView } from "@app/client/xhandler";
import { ClearFiltersButton } from "@app/components/ClearFiltersButton";
import { ConfirmActionModal } from "@app/components/ConfirmActionModal";
import { dataViewBodyStates } from "@app/components/dataViewStates";
import { DocumentTitle } from "@app/components/DocumentTitle";
import { ListPagination } from "@app/components/ListPagination";
import { SingleSelectFilter } from "@app/components/SingleSelectFilter";
import { TableEmptyState } from "@app/components/TableEmptyState";
import { TenantContextSelector } from "@app/components/TenantContextSelector";
import {
  DEFAULT_REFETCH_INTERVAL,
  RENDER_DATETIME_FORMAT,
} from "@app/constants";
import { useDebouncedValue } from "@app/hooks/useDebouncedValue";
import { useNotifications } from "@app/hooks/useNotifications";
import { usePersistentState } from "@app/hooks/usePersistentState";
import { useTenantRole } from "@app/hooks/useTenantRole";
import {
  isJobCancelable,
  isTerminalJobState,
  JOB_STATES,
  useJobCancelMutation,
  useJobsListQuery,
} from "@app/queries/xhandler/jobs";
import { getMutationErrorMessage } from "@app/utils/apiError";
import type { DataViewTh } from "@app/utils/dataViewTypes";
import { estimateItemCount, toPageParams } from "@app/utils/listState";

import {
  JOB_STATE_PRESENTATION,
  JobStateLabel,
} from "./components/JobStateLabel";
import { JOB_LIST_STATE } from "./jobListState";

const STATE_OPTIONS = JOB_STATES.map((state) => ({
  value: state,
  label: JOB_STATE_PRESENTATION[state].label,
}));

const formatTime = (value: string | undefined): string =>
  value ? dayjs(value).format(RENDER_DATETIME_FORMAT) : "-";

/** A failed job's error as the server wrote it (`message`, else `description`). */
const jobErrorText = (job: JobView): string | undefined => {
  const message = job.error?.message ?? job.error?.description;
  return typeof message === "string" ? message : undefined;
};

/** The tenant's background jobs, shaped like Pulp's tasks (ADR 0007). */
export const JobList: React.FC = () => {
  const { addNotification } = useNotifications();
  const { can, disabledProps } = useTenantRole();
  const [cancelTarget, setCancelTarget] = useState<JobView | null>(null);

  const [pagination, setPagination] = usePersistentState({
    ...JOB_LIST_STATE.pagination,
    storage: "url",
  });
  const [filters, setFilters] = usePersistentState({
    ...JOB_LIST_STATE.filters,
    storage: "url",
  });
  const toFirstPage = () => setPagination((prev) => ({ ...prev, page: 1 }));
  const clearAllFilters = () => {
    setFilters(JOB_LIST_STATE.filters.defaultValue);
    toFirstPage();
  };

  const debouncedNombre = useDebouncedValue({ value: filters.nombre.trim() });

  const { data, isLoading, error } = useJobsListQuery({
    params: {
      ...toPageParams(pagination),
      state: filters.estado[0],
      name: debouncedNombre || undefined,
    },
    options: {
      // Server-derived state: poll while a job on the page can still change.
      refetchInterval: (query) =>
        query.state.data?.items?.some((job) => !isTerminalJobState(job.state))
          ? DEFAULT_REFETCH_INTERVAL
          : false,
    },
  });
  const jobs = data?.items ?? [];

  const cancelJob = useJobCancelMutation({
    onSuccess: (job) =>
      addNotification({
        variant: "info",
        title: `Se solicitó cancelar el trabajo ${job.name ?? ""}`.trim(),
      }),
    onError: (err) =>
      addNotification({
        variant: "danger",
        ...getMutationErrorMessage({
          error: err,
          fallback: "No se pudo cancelar el trabajo",
        }),
      }),
  });

  const actionsFor = (job: JobView): IAction[] => [
    {
      title: "Cancelar",
      onClick: () => setCancelTarget(job),
      isAriaDisabled: !can("operator") || !isJobCancelable(job.state),
      tooltipProps: !can("operator")
        ? disabledProps("operator").tooltipProps
        : !isJobCancelable(job.state)
          ? { content: "Solo se cancela un trabajo en espera o en ejecución." }
          : undefined,
    },
  ];

  const columns: DataViewTh[] = [
    { cell: "Trabajo", props: { width: 25 } },
    { cell: "Estado", props: { width: 15 } },
    { cell: "Creado", props: { width: 15 } },
    { cell: "Finalizado", props: { width: 15 } },
    { cell: "Error", props: { width: 30 } },
    { cell: "" },
  ];

  const rows = jobs.map((job) => ({
    id: job.id ?? "",
    row: [
      { cell: job.name ?? "-", props: { modifier: "truncate" as const } },
      { cell: <JobStateLabel state={job.state} /> },
      { cell: formatTime(job.created_at) },
      { cell: formatTime(job.finished_at) },
      {
        cell: jobErrorText(job) ?? "-",
        props: { modifier: "truncate" as const },
      },
      {
        cell: (
          <ActionsColumn
            items={actionsFor(job)}
            popperProps={{ position: "right" }}
          />
        ),
        props: { isActionCell: true },
      },
    ],
  }));

  const isFiltered = filters.estado.length > 0 || Boolean(debouncedNombre);

  const { activeState, bodyStates } = dataViewBodyStates({
    columnCount: columns.length,
    isLoading,
    error,
    isEmpty: rows.length === 0,
    emptyState: (
      <TableEmptyState
        title="No hay trabajos"
        body="Los envíos a SUNAT, reportes y otras tareas en segundo plano aparecerán aquí."
        isFiltered={isFiltered}
        onClearFilters={clearAllFilters}
      />
    ),
  });

  const pager = (variant: PaginationVariant) => (
    <ListPagination
      variant={variant}
      itemCount={estimateItemCount({ pagination, pageSize: jobs.length })}
      isCountEstimated
      pagination={pagination}
      onPaginationChange={setPagination}
    />
  );

  return (
    <>
      <DocumentTitle title="Trabajos" />
      <TenantContextSelector />
      <PageSection>
        <Content component={ContentVariants.h1}>Trabajos</Content>
        <Content component={ContentVariants.p}>
          Las tareas en segundo plano de esta organización y su estado.
        </Content>
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
                <DataViewTextFilter
                  filterId="nombre"
                  title="Nombre"
                  placeholder="Filtrar por nombre"
                />
                {/* The API filters one state at a time. */}
                <SingleSelectFilter
                  filterId="estado"
                  title="Estado"
                  placeholder="Filtrar por estado"
                  options={STATE_OPTIONS}
                />
              </DataViewFilters>
            }
            pagination={pager(PaginationVariant.top)}
          />
          <DataViewTable
            aria-label="Tabla de trabajos"
            columns={columns}
            rows={rows}
            bodyStates={bodyStates}
          />
          <DataViewToolbar pagination={pager(PaginationVariant.bottom)} />
        </DataView>
      </PageSection>

      <ConfirmActionModal
        isOpen={cancelTarget !== null}
        title="¿Cancelar el trabajo?"
        body={`El trabajo ${cancelTarget?.name ?? ""} se detendrá; si ya está en ejecución, terminará su paso actual.`}
        confirmLabel="Cancelar trabajo"
        cancelLabel="Volver"
        confirmVariant="primary"
        titleIconVariant="warning"
        isConfirming={cancelJob.isPending}
        onConfirm={() =>
          cancelTarget &&
          cancelJob.mutate(cancelTarget, {
            onSuccess: () => setCancelTarget(null),
          })
        }
        onCancel={() => setCancelTarget(null)}
      />
    </>
  );
};
