import type React from "react";
import { useState } from "react";

import dayjs from "dayjs";

import { PaginationVariant } from "@patternfly/react-core";
import {
  DataView,
  DataViewTable,
  DataViewToolbar,
} from "@patternfly/react-data-view";
import { ActionsColumn, type IAction } from "@patternfly/react-table";

import type { Suppression } from "@app/client/xhandler";
import { ConfirmActionModal } from "@app/components/ConfirmActionModal";
import { dataViewBodyStates } from "@app/components/dataViewStates";
import { ListPagination } from "@app/components/ListPagination";
import { TableEmptyState } from "@app/components/TableEmptyState";
import { RENDER_DATETIME_FORMAT } from "@app/constants";
import { useNotifications } from "@app/hooks/useNotifications";
import { usePersistentState } from "@app/hooks/usePersistentState";
import { useTenantRole } from "@app/hooks/useTenantRole";
import {
  useEmailSuppressionRemoveMutation,
  useEmailSuppressionsListQuery,
} from "@app/queries/xhandler/email";
import { getMutationErrorMessage } from "@app/utils/apiError";
import type { DataViewTh } from "@app/utils/dataViewTypes";
import { PAGINATION_STATE, toPageParams } from "@app/utils/listState";

/** Why the platform stopped mailing an address, in Spanish when the reason is known. */
const REASON_LABELS: Record<string, string> = {
  bounce: "Rebote",
  hard_bounce: "Rebote permanente",
  complaint: "Marcado como spam",
};

/** Addresses that bounced or complained: no mail goes to them until removed here. */
export const EmailSuppressionTable: React.FC = () => {
  const { addNotification } = useNotifications();
  const { can, disabledProps } = useTenantRole();
  const [removeTarget, setRemoveTarget] = useState<Suppression | null>(null);
  const [pagination, setPagination] = usePersistentState({
    ...PAGINATION_STATE,
    storage: "url",
  });

  // GET /email-suppressions is unpaged by the API: read whole, paged on screen.
  const { data, isLoading, error } = useEmailSuppressionsListQuery({});
  const suppressions = data ?? [];
  const { limit, offset } = toPageParams(pagination);
  const pageSuppressions = suppressions.slice(offset, offset + limit);

  const remove = useEmailSuppressionRemoveMutation({
    onSuccess: (_data, suppression) =>
      addNotification({
        variant: "success",
        title:
          `Se volverá a enviar correo a ${suppression.address ?? ""}`.trim(),
      }),
    onError: (err) =>
      addNotification({
        variant: "danger",
        ...getMutationErrorMessage({
          error: err,
          fallback: "No se pudo quitar la dirección de la lista",
        }),
      }),
  });

  const actionsFor = (suppression: Suppression): IAction[] => [
    {
      title: "Quitar de la lista",
      onClick: () => setRemoveTarget(suppression),
      isAriaDisabled: !can("admin"),
      tooltipProps: disabledProps("admin").tooltipProps,
    },
  ];

  const columns: DataViewTh[] = [
    { cell: "Dirección", props: { width: 50 } },
    { cell: "Motivo", props: { width: 25 } },
    { cell: "Desde", props: { width: 25 } },
    { cell: "" },
  ];

  const rows = pageSuppressions.map((suppression) => ({
    id: suppression.address ?? "",
    row: [
      {
        cell: suppression.address ?? "-",
        props: { modifier: "truncate" as const },
      },
      {
        cell: suppression.reason
          ? (REASON_LABELS[suppression.reason] ?? suppression.reason)
          : "-",
      },
      {
        cell: suppression.created_at
          ? dayjs(suppression.created_at).format(RENDER_DATETIME_FORMAT)
          : "-",
      },
      {
        cell: (
          <ActionsColumn
            items={actionsFor(suppression)}
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
        title="No hay direcciones suprimidas"
        body="Las direcciones que rebotan o marcan los correos como spam aparecerán aquí."
      />
    ),
  });

  const pager = (variant: PaginationVariant) => (
    <ListPagination
      variant={variant}
      itemCount={suppressions.length}
      pagination={pagination}
      onPaginationChange={setPagination}
    />
  );

  return (
    <>
      <DataView activeState={activeState}>
        <DataViewToolbar pagination={pager(PaginationVariant.top)} />
        <DataViewTable
          aria-label="Tabla de direcciones suprimidas"
          columns={columns}
          rows={rows}
          bodyStates={bodyStates}
        />
        <DataViewToolbar pagination={pager(PaginationVariant.bottom)} />
      </DataView>
      <ConfirmActionModal
        isOpen={removeTarget !== null}
        title="¿Quitar la dirección de la lista?"
        body={`Se volverá a enviar correo a ${removeTarget?.address ?? ""}. Si vuelve a rebotar, regresará a la lista.`}
        confirmLabel="Quitar"
        cancelLabel="Volver"
        confirmVariant="primary"
        isConfirming={remove.isPending}
        onConfirm={() =>
          removeTarget &&
          remove.mutate(removeTarget, {
            onSuccess: () => setRemoveTarget(null),
          })
        }
        onCancel={() => setRemoveTarget(null)}
      />
    </>
  );
};
