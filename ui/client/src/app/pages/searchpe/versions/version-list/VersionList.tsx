import type React from "react";
import { useState } from "react";

import dayjs from "dayjs";

import {
  Content,
  ContentVariants,
  Label,
  PageSection,
  PaginationVariant,
} from "@patternfly/react-core";
import {
  DataView,
  DataViewTable,
  DataViewToolbar,
} from "@patternfly/react-data-view";
import { ActionsColumn, type IAction } from "@patternfly/react-table";

import type { VersionDto } from "@app/client/searchpe";
import { ConfirmActionModal } from "@app/components/ConfirmActionModal";
import { dataViewBodyStates } from "@app/components/dataViewStates";
import { DocumentTitle } from "@app/components/DocumentTitle";
import { GatedButton } from "@app/components/GatedButton";
import { ListPagination } from "@app/components/ListPagination";
import { TableEmptyState } from "@app/components/TableEmptyState";
import { UnauthorizedState } from "@app/components/UnauthorizedState";
import { RENDER_DATETIME_FORMAT } from "@app/constants";
import { useNotifications } from "@app/hooks/useNotifications";
import { usePersistentState } from "@app/hooks/usePersistentState";
import { useSearchpePermissions } from "@app/hooks/useSearchpePermissions";
import {
  isVersionCancellable,
  isVersionDeletable,
  isVersionFinished,
  useVersionCancelMutation,
  useVersionCreateMutation,
  useVersionDeleteMutation,
  useVersionsListQuery,
  VERSIONS_REFETCH_INTERVAL,
} from "@app/queries/searchpe/versions";
import { getMutationErrorMessage } from "@app/utils/apiError";
import type { DataViewTh } from "@app/utils/dataViewTypes";
import { toPageParams } from "@app/utils/listState";

import { VersionStatusLabel } from "./components/VersionStatusLabel";
import { VERSION_LIST_STATE } from "./versionListState";

/** The padrón versions: each import of SUNAT's padrón reducido, newest first. */
export const VersionList: React.FC = () => {
  const { addNotification } = useNotifications();
  const { can, disabledProps } = useSearchpePermissions();

  const [pagination, setPagination] = usePersistentState({
    ...VERSION_LIST_STATE.pagination,
    storage: "url",
  });
  const [cancelTarget, setCancelTarget] = useState<VersionDto | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<VersionDto | null>(null);

  const { data, isLoading, error } = useVersionsListQuery({
    options: {
      enabled: can("version:write"),
      // Poll while an import or deletion runs, so its status moves on screen.
      refetchInterval: (query) =>
        query.state.data?.some((version) => !isVersionFinished(version.status))
          ? VERSIONS_REFETCH_INTERVAL
          : false,
    },
  });

  const createVersion = useVersionCreateMutation({
    onSuccess: (version) =>
      addNotification({
        variant: "info",
        title: `Se programó la importación de la versión #${version.id}`,
        description:
          "searchpe descargará e importará el padrón reducido de la SUNAT.",
      }),
    onError: (err) =>
      addNotification({
        variant: "danger",
        ...getMutationErrorMessage({
          error: err,
          fallback: "No se pudo crear la versión",
        }),
      }),
  });

  const cancelVersion = useVersionCancelMutation({
    onSuccess: (_data, version) =>
      addNotification({
        variant: "info",
        title: `Se solicitó cancelar la versión #${version.id}`,
      }),
    onError: (err, version) =>
      addNotification({
        variant: "danger",
        ...getMutationErrorMessage({
          error: err,
          fallback: `No se pudo cancelar la versión #${version.id}`,
        }),
      }),
  });

  const deleteVersion = useVersionDeleteMutation({
    onSuccess: (_data, version) =>
      addNotification({
        variant: "info",
        title: `Se está eliminando la versión #${version.id}`,
      }),
    onError: (err, version) =>
      addNotification({
        variant: "danger",
        ...getMutationErrorMessage({
          error: err,
          fallback: `No se pudo eliminar la versión #${version.id}`,
        }),
      }),
  });

  const versions = data ?? [];
  // The endpoint answers every version (no paging); the table pages them here.
  const { limit, offset } = toPageParams(pagination);
  const pageItems = versions.slice(offset, offset + limit);

  const actionsFor = (version: VersionDto): IAction[] => [
    {
      title: "Cancelar importación",
      onClick: () => setCancelTarget(version),
      isAriaDisabled:
        !can("version:write") || !isVersionCancellable(version.status),
      tooltipProps: !can("version:write")
        ? disabledProps("version:write").tooltipProps
        : !isVersionCancellable(version.status)
          ? { content: "Solo se cancela una importación en curso." }
          : undefined,
    },
    {
      title: "Eliminar",
      onClick: () => setDeleteTarget(version),
      isAriaDisabled:
        !can("version:write") || !isVersionDeletable(version.status),
      tooltipProps: !can("version:write")
        ? disabledProps("version:write").tooltipProps
        : !isVersionDeletable(version.status)
          ? { content: "Espera a que la versión termine para eliminarla." }
          : undefined,
    },
  ];

  const columns: DataViewTh[] = [
    { cell: "Versión", props: { width: 15 } },
    { cell: "Creada", props: { width: 25 } },
    { cell: "Registros", props: { width: 20 } },
    { cell: "Activa", props: { width: 15 } },
    { cell: "Estado", props: { width: 25 } },
    { cell: "" },
  ];

  const rows = pageItems.map((version) => ({
    id: String(version.id),
    row: [
      { cell: `#${version.id}` },
      {
        cell: version.createdAt
          ? dayjs(version.createdAt).format(RENDER_DATETIME_FORMAT)
          : "-",
      },
      { cell: (version.records ?? 0).toLocaleString("es-PE") },
      {
        cell: version.active ? (
          <Label color="green" isCompact>
            Activa
          </Label>
        ) : (
          <Label isCompact>Inactiva</Label>
        ),
      },
      { cell: <VersionStatusLabel status={version.status} /> },
      {
        cell: (
          <ActionsColumn
            items={actionsFor(version)}
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
        title="No hay versiones del padrón"
        body="Crea una versión para importar el padrón reducido de la SUNAT."
      />
    ),
  });

  return (
    <>
      <DocumentTitle title="Versiones del padrón" />
      <PageSection>
        <Content component={ContentVariants.h1}>Versiones del padrón</Content>
        <Content component={ContentVariants.p}>
          Cada versión es una importación del padrón reducido de la SUNAT; las
          consultas usan la versión activa.
        </Content>
      </PageSection>
      <PageSection>
        {can("version:write") ? (
          <DataView activeState={activeState}>
            <DataViewToolbar
              actions={
                <GatedButton
                  variant="primary"
                  onClick={() => createVersion.mutate()}
                  isLoading={createVersion.isPending}
                  {...disabledProps("version:write")}
                >
                  Nueva versión
                </GatedButton>
              }
              pagination={
                <ListPagination
                  variant={PaginationVariant.top}
                  itemCount={versions.length}
                  pagination={pagination}
                  onPaginationChange={setPagination}
                />
              }
            />
            <DataViewTable
              aria-label="Tabla de versiones del padrón"
              columns={columns}
              rows={rows}
              bodyStates={bodyStates}
            />
          </DataView>
        ) : (
          <UnauthorizedState description="Necesitas el permiso version:write en searchpe para administrar las versiones." />
        )}
      </PageSection>

      <ConfirmActionModal
        isOpen={cancelTarget !== null}
        title="¿Cancelar la importación?"
        body={`La versión #${cancelTarget?.id ?? ""} dejará de importarse y quedará cancelada.`}
        confirmLabel="Cancelar importación"
        cancelLabel="Volver"
        confirmVariant="primary"
        titleIconVariant="warning"
        isConfirming={cancelVersion.isPending}
        onConfirm={() =>
          cancelTarget &&
          cancelVersion.mutate(cancelTarget, {
            onSuccess: () => setCancelTarget(null),
          })
        }
        onCancel={() => setCancelTarget(null)}
      />
      <ConfirmActionModal
        isOpen={deleteTarget !== null}
        title="¿Eliminar la versión?"
        body={`La versión #${deleteTarget?.id ?? ""} y sus registros se eliminarán de forma permanente.`}
        confirmLabel="Eliminar"
        titleIconVariant="warning"
        isConfirming={deleteVersion.isPending}
        onConfirm={() =>
          deleteTarget &&
          deleteVersion.mutate(deleteTarget, {
            onSuccess: () => setDeleteTarget(null),
          })
        }
        onCancel={() => setDeleteTarget(null)}
      />
    </>
  );
};
