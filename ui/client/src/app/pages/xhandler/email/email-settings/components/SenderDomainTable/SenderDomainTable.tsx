import type React from "react";
import { useState } from "react";

import dayjs from "dayjs";

import {
  ClipboardCopy,
  Label,
  PaginationVariant,
  ToolbarItem,
} from "@patternfly/react-core";
import {
  DataView,
  DataViewTable,
  DataViewToolbar,
} from "@patternfly/react-data-view";
import { ActionsColumn, type IAction } from "@patternfly/react-table";

import type { DomainView } from "@app/client/xhandler";
import { dataViewBodyStates } from "@app/components/dataViewStates";
import { GatedButton } from "@app/components/GatedButton";
import { ListPagination } from "@app/components/ListPagination";
import { TableEmptyState } from "@app/components/TableEmptyState";
import { RENDER_DATETIME_FORMAT } from "@app/constants";
import { useNotifications } from "@app/hooks/useNotifications";
import { usePersistentState } from "@app/hooks/usePersistentState";
import { useTenantRole } from "@app/hooks/useTenantRole";
import {
  useSenderDomainsListQuery,
  useSenderDomainVerifyMutation,
} from "@app/queries/xhandler/email";
import { getMutationErrorMessage } from "@app/utils/apiError";
import type { DataViewTh } from "@app/utils/dataViewTypes";
import { PAGINATION_STATE, toPageParams } from "@app/utils/listState";

import { AddSenderDomainModal } from "../AddSenderDomainModal";

/** Inline copy; `inline-compact` ignores `textAriaLabel`, so the button carries the name. */
const copyable = (text: string | undefined, copyAriaLabel: string) =>
  text ? (
    <ClipboardCopy
      isReadOnly
      variant="inline-compact"
      hoverTip="Copiar"
      clickTip="Copiado"
      copyAriaLabel={copyAriaLabel}
    >
      {text}
    </ClipboardCopy>
  ) : (
    "-"
  );

/** The tenant's sender domains and the TXT records that verify them (DESIGN.md §11.6). */
export const SenderDomainTable: React.FC = () => {
  const { addNotification } = useNotifications();
  const { can, disabledProps } = useTenantRole();
  const [isAddOpen, setIsAddOpen] = useState(false);
  const [pagination, setPagination] = usePersistentState({
    ...PAGINATION_STATE,
    storage: "url",
  });

  // GET /sender-domains is unpaged (a handful per tenant): read whole, paged on screen.
  const { data, isLoading, error } = useSenderDomainsListQuery({});
  const domains = data ?? [];
  const { limit, offset } = toPageParams(pagination);
  const pageDomains = domains.slice(offset, offset + limit);

  const verify = useSenderDomainVerifyMutation({
    // A missing TXT record answers 409 with the record it looked for.
    onSuccess: (domain) =>
      addNotification({
        variant: "success",
        title: `El dominio ${domain.domain ?? ""} está verificado`,
      }),
    onError: (err) =>
      addNotification({
        variant: "danger",
        ...getMutationErrorMessage({
          error: err,
          fallback: "No se pudo verificar el dominio",
        }),
      }),
  });

  const actionsFor = (domain: DomainView): IAction[] => [
    {
      title: "Verificar",
      onClick: () => verify.mutate(domain),
      isAriaDisabled: !can("admin"),
      tooltipProps: disabledProps("admin").tooltipProps,
    },
  ];

  const columns: DataViewTh[] = [
    { cell: "Dominio", props: { width: 20 } },
    { cell: "Registro TXT (nombre)", props: { width: 30 } },
    { cell: "Registro TXT (valor)", props: { width: 30 } },
    { cell: "Estado", props: { width: 20 } },
    { cell: "" },
  ];

  const rows = pageDomains.map((domain) => ({
    id: domain.id ?? domain.domain ?? "",
    row: [
      { cell: domain.domain ?? "-" },
      { cell: copyable(domain.txt_name, "Copiar el nombre del registro TXT") },
      { cell: copyable(domain.txt_value, "Copiar el valor del registro TXT") },
      {
        cell: domain.verified_at ? (
          <Label color="green" isCompact>
            Verificado el{" "}
            {dayjs(domain.verified_at).format(RENDER_DATETIME_FORMAT)}
          </Label>
        ) : (
          <Label color="orange" isCompact>
            Sin verificar
          </Label>
        ),
      },
      {
        cell: (
          <ActionsColumn
            items={actionsFor(domain)}
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
        title="No hay dominios remitentes"
        body="Sin un dominio verificado, los correos salen con la dirección de la plataforma."
      />
    ),
  });

  const pager = (variant: PaginationVariant) => (
    <ListPagination
      variant={variant}
      itemCount={domains.length}
      pagination={pagination}
      onPaginationChange={setPagination}
    />
  );

  return (
    <>
      <DataView activeState={activeState}>
        <DataViewToolbar
          actions={
            <ToolbarItem>
              <GatedButton
                variant="primary"
                onClick={() => setIsAddOpen(true)}
                {...disabledProps("admin")}
              >
                Nuevo dominio
              </GatedButton>
            </ToolbarItem>
          }
          pagination={pager(PaginationVariant.top)}
        />
        <DataViewTable
          aria-label="Tabla de dominios remitentes"
          columns={columns}
          rows={rows}
          bodyStates={bodyStates}
        />
        <DataViewToolbar pagination={pager(PaginationVariant.bottom)} />
      </DataView>
      {isAddOpen ? (
        <AddSenderDomainModal onClose={() => setIsAddOpen(false)} />
      ) : null}
    </>
  );
};
