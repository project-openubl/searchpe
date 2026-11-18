import type React from "react";
import { useState } from "react";

import { Link, useNavigate } from "@tanstack/react-router";
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
  DataViewToolbar,
} from "@patternfly/react-data-view";
import { ActionsColumn, type IAction } from "@patternfly/react-table";

import type { DraftView } from "@app/client/xhandler";
import { ClearFiltersButton } from "@app/components/ClearFiltersButton";
import { ConfirmActionModal } from "@app/components/ConfirmActionModal";
import { dataViewBodyStates } from "@app/components/dataViewStates";
import { DocumentTitle } from "@app/components/DocumentTitle";
import { GatedButton } from "@app/components/GatedButton";
import { ListPagination } from "@app/components/ListPagination";
import { SingleSelectFilter } from "@app/components/SingleSelectFilter";
import { TableEmptyState } from "@app/components/TableEmptyState";
import { TenantContextSelector } from "@app/components/TenantContextSelector";
import { RENDER_DATETIME_FORMAT } from "@app/constants";
import { useNotifications } from "@app/hooks/useNotifications";
import { usePersistentState } from "@app/hooks/usePersistentState";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";
import { useTenantRole } from "@app/hooks/useTenantRole";
import {
  DRAFT_STATUSES,
  useDraftDeleteMutation,
  useDraftsListQuery,
} from "@app/queries/xhandler/drafts";
import { getMutationErrorMessage } from "@app/utils/apiError";
import type { DataViewTh } from "@app/utils/dataViewTypes";
import { draftTypeLabel } from "@app/utils/draftTypes";
import { estimateItemCount, toPageParams } from "@app/utils/listState";

import { DraftCreateModal } from "./components/DraftCreateModal";
import {
  DRAFT_STATUS_PRESENTATION,
  DraftStatusLabel,
} from "./components/DraftStatusLabel";
import { DRAFT_LIST_STATE } from "./draftListState";

const STATUS_OPTIONS = DRAFT_STATUSES.map((status) => ({
  value: status,
  label: DRAFT_STATUS_PRESENTATION[status].label,
}));

const formatTime = (value: string | undefined): string =>
  value ? dayjs(value).format(RENDER_DATETIME_FORMAT) : "-";

/** Manual issuing (DESIGN.md §11.10): the tenant's drafts, the newest first. */
export const DraftList: React.FC = () => {
  const tenantId = useSelectedTenantId();
  const navigate = useNavigate();
  const { addNotification } = useNotifications();
  const { can, disabledProps } = useTenantRole();
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<DraftView | null>(null);

  const [pagination, setPagination] = usePersistentState({
    ...DRAFT_LIST_STATE.pagination,
    storage: "url",
  });
  const [filters, setFilters] = usePersistentState({
    ...DRAFT_LIST_STATE.filters,
    storage: "url",
  });
  const toFirstPage = () => setPagination((prev) => ({ ...prev, page: 1 }));
  const clearAllFilters = () => {
    setFilters(DRAFT_LIST_STATE.filters.defaultValue);
    toFirstPage();
  };

  const { data, isLoading, error } = useDraftsListQuery({
    params: { ...toPageParams(pagination), status: filters.estado[0] },
  });
  const drafts = data?.items ?? [];

  const deleteDraft = useDraftDeleteMutation({
    onSuccess: () =>
      addNotification({ variant: "success", title: "Se eliminó el borrador" }),
    onError: (err) =>
      addNotification({
        variant: "danger",
        ...getMutationErrorMessage({
          error: err,
          fallback: "No se pudo eliminar el borrador",
        }),
      }),
  });

  const open = (draft: DraftView) =>
    draft.id &&
    navigate({
      to: "/organizaciones/$tenantId/borradores/$draftId",
      params: { tenantId, draftId: draft.id },
    });

  const actionsFor = (draft: DraftView): IAction[] => [
    {
      title: draft.status === "issued" ? "Ver" : "Editar",
      onClick: () => void open(draft),
    },
    {
      title: "Eliminar",
      onClick: () => setDeleteTarget(draft),
      isAriaDisabled: !can("operator") || draft.status !== "draft",
      tooltipProps: !can("operator")
        ? disabledProps("operator").tooltipProps
        : draft.status !== "draft"
          ? { content: "Un borrador emitido no se elimina." }
          : undefined,
    },
  ];

  const columns: DataViewTh[] = [
    { cell: "Comprobante", props: { width: 25 } },
    { cell: "Cliente", props: { width: 30 } },
    { cell: "Serie", props: { width: 10 } },
    { cell: "Estado", props: { width: 15 } },
    { cell: "Actualizado", props: { width: 20 } },
    { cell: "" },
  ];

  const rows = drafts.map((draft) => ({
    id: draft.id ?? "",
    row: [
      {
        cell: draft.id ? (
          <Link
            to="/organizaciones/$tenantId/borradores/$draftId"
            params={{ tenantId, draftId: draft.id }}
          >
            {draftTypeLabel(draft)}
          </Link>
        ) : (
          draftTypeLabel(draft)
        ),
      },
      {
        cell: draft.customer?.nombre || "-",
        props: { modifier: "truncate" as const },
      },
      { cell: draft.serie ?? "Por defecto" },
      { cell: <DraftStatusLabel status={draft.status} /> },
      { cell: formatTime(draft.updated_at) },
      {
        cell: (
          <ActionsColumn
            items={actionsFor(draft)}
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
        title="No hay borradores"
        body="Los comprobantes que prepares a mano aparecerán aquí hasta emitirlos."
        isFiltered={isFiltered}
        onClearFilters={clearAllFilters}
      />
    ),
  });

  const pager = (variant: PaginationVariant) => (
    <ListPagination
      variant={variant}
      itemCount={estimateItemCount({ pagination, pageSize: drafts.length })}
      isCountEstimated
      pagination={pagination}
      onPaginationChange={setPagination}
    />
  );

  return (
    <>
      <DocumentTitle title="Emisión manual" />
      <TenantContextSelector />
      <PageSection>
        <Content component={ContentVariants.h1}>Emisión manual</Content>
        <Content component={ContentVariants.p}>
          Prepara facturas, boletas y notas como borradores, sin número, y
          emítelas cuando estén listas.
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
                {/* The API filters one status at a time. */}
                <SingleSelectFilter
                  filterId="estado"
                  title="Estado"
                  placeholder="Filtrar por estado"
                  options={STATUS_OPTIONS}
                />
              </DataViewFilters>
            }
            actions={
              <GatedButton
                variant="primary"
                {...disabledProps("operator")}
                onClick={() => setIsCreateOpen(true)}
              >
                Nuevo borrador
              </GatedButton>
            }
            pagination={pager(PaginationVariant.top)}
          />
          <DataViewTable
            aria-label="Tabla de borradores"
            columns={columns}
            rows={rows}
            bodyStates={bodyStates}
          />
          <DataViewToolbar pagination={pager(PaginationVariant.bottom)} />
        </DataView>
      </PageSection>

      <DraftCreateModal
        isOpen={isCreateOpen}
        onClose={() => setIsCreateOpen(false)}
      />
      <ConfirmActionModal
        isOpen={deleteTarget !== null}
        title="¿Eliminar el borrador?"
        body="El borrador se eliminará; no se emitió, así que no consume ningún número."
        confirmLabel="Eliminar"
        isConfirming={deleteDraft.isPending}
        onConfirm={() =>
          deleteTarget &&
          deleteDraft.mutate(deleteTarget, {
            onSuccess: () => setDeleteTarget(null),
          })
        }
        onCancel={() => setDeleteTarget(null)}
      />
    </>
  );
};
