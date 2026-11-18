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

import type { NubefactToken } from "@app/client/xhandler";
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
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";
import { useTenantRole } from "@app/hooks/useTenantRole";
import {
  type IAdapterTokenRow,
  toAdapterTokenRow,
  useAdapterTokenRevokeMutation,
  useAdapterTokensListQuery,
} from "@app/queries/xhandler/adapter-tokens";
import { useSuspenseCompanyDetailQuery } from "@app/queries/xhandler/companies";
import { getMutationErrorMessage } from "@app/utils/apiError";
import type { DataViewTh } from "@app/utils/dataViewTypes";
import { PAGINATION_LIST_STATE, toPageParams } from "@app/utils/listState";

import { AdapterTokenCreatedModal } from "./components/AdapterTokenCreatedModal";
import { CreateAdapterTokenModal } from "./components/CreateAdapterTokenModal";

interface IAdapterTokenListProps {
  companyId: string;
}

const KIND_LABELS: Record<string, string> = { nubefact: "Nubefact" };

const formatTime = (value: string | undefined): string =>
  value ? dayjs(value).format(RENDER_DATETIME_FORMAT) : "-";

/** The company's adapter tokens (Nubefact-compatible API for existing ERPs); admins only. */
export const AdapterTokenList: React.FC<IAdapterTokenListProps> = ({
  companyId,
}) => {
  const tenantId = useSelectedTenantId();
  const { addNotification } = useNotifications();
  const { can } = useTenantRole();
  const { data: company } = useSuspenseCompanyDetailQuery({ companyId });
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [createdToken, setCreatedToken] = useState<NubefactToken | null>(null);
  const [revokeTarget, setRevokeTarget] = useState<IAdapterTokenRow | null>(
    null,
  );

  const [pagination, setPagination] = usePersistentState({
    ...PAGINATION_LIST_STATE.pagination,
    storage: "url",
  });

  const isAdmin = can("admin");
  // GET …/adapter-tokens is unpaged (a company has a few): read whole, paged on screen.
  const { data, isLoading, error } = useAdapterTokensListQuery({
    companyId,
    options: { enabled: isAdmin },
  });
  const tokens = (data ?? []).map(toAdapterTokenRow);
  const { limit, offset } = toPageParams(pagination);
  const pageTokens = tokens.slice(offset, offset + limit);

  const revoke = useAdapterTokenRevokeMutation({
    onSuccess: (_data, { token }) =>
      addNotification({
        variant: "success",
        title: `Se revocó el token ${token.label}`,
      }),
    onError: (err) =>
      addNotification({
        variant: "danger",
        ...getMutationErrorMessage({
          error: err,
          fallback: "No se pudo revocar el token",
        }),
      }),
  });

  const actionsFor = (token: IAdapterTokenRow): IAction[] => [
    {
      title: "Revocar",
      onClick: () => setRevokeTarget(token),
      isAriaDisabled: !!token.revokedAt,
      tooltipProps: token.revokedAt
        ? { content: "El token ya está revocado." }
        : undefined,
    },
  ];

  const columns: DataViewTh[] = [
    { cell: "Etiqueta", props: { width: 30 } },
    { cell: "Tipo", props: { width: 15 } },
    { cell: "Creado por", props: { width: 20 } },
    { cell: "Creado", props: { width: 20 } },
    { cell: "Estado", props: { width: 15 } },
    { cell: "" },
  ];

  const rows = pageTokens.map((token) => ({
    id: token.id,
    row: [
      { cell: token.label || "-", props: { modifier: "truncate" as const } },
      { cell: KIND_LABELS[token.kind] ?? token.kind },
      {
        cell: token.createdBy || "-",
        props: { modifier: "truncate" as const },
      },
      { cell: formatTime(token.createdAt) },
      {
        cell: token.revokedAt ? (
          <Label color="grey" isCompact>
            Revocado el {formatTime(token.revokedAt)}
          </Label>
        ) : (
          <Label color="green" isCompact>
            Activo
          </Label>
        ),
      },
      {
        cell: (
          <ActionsColumn
            items={actionsFor(token)}
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
        title="No hay tokens"
        body="Crea un token para que tu ERP emita por la API compatible con Nubefact."
      />
    ),
  });

  const pager = (variant: PaginationVariant) => (
    <ListPagination
      variant={variant}
      itemCount={tokens.length}
      pagination={pagination}
      onPaginationChange={setPagination}
    />
  );

  return (
    <>
      <DocumentTitle title={`Tokens de adaptadores · ${company.ruc ?? ""}`} />
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
          <BreadcrumbItem isActive>Tokens de adaptadores</BreadcrumbItem>
        </Breadcrumb>
        <Content component={ContentVariants.h1}>Tokens de adaptadores</Content>
        <Content component={ContentVariants.p}>
          Tokens con los que un ERP emite para{" "}
          {company.razon_social || company.ruc} por la API compatible con
          Nubefact.
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
                    onClick={() => setIsCreateOpen(true)}
                  >
                    Nuevo token
                  </GatedButton>
                </ToolbarItem>
              }
              pagination={pager(PaginationVariant.top)}
            />
            <DataViewTable
              aria-label="Tabla de tokens de adaptadores"
              columns={columns}
              rows={rows}
              bodyStates={bodyStates}
            />
            <DataViewToolbar pagination={pager(PaginationVariant.bottom)} />
          </DataView>
        ) : (
          <UnauthorizedState />
        )}
      </PageSection>

      {isCreateOpen ? (
        <CreateAdapterTokenModal
          companyId={companyId}
          onCreated={(token) => {
            setIsCreateOpen(false);
            setCreatedToken(token);
          }}
          onClose={() => setIsCreateOpen(false)}
        />
      ) : null}
      {createdToken ? (
        <AdapterTokenCreatedModal
          token={createdToken}
          onClose={() => setCreatedToken(null)}
        />
      ) : null}
      <ConfirmActionModal
        isOpen={revokeTarget !== null}
        title="¿Revocar el token?"
        body={`El ERP que use el token ${revokeTarget?.label ?? ""} dejará de poder emitir. No se puede deshacer.`}
        confirmLabel="Revocar token"
        cancelLabel="Volver"
        isConfirming={revoke.isPending}
        onConfirm={() =>
          revokeTarget &&
          revoke.mutate(
            { companyId, token: revokeTarget },
            { onSuccess: () => setRevokeTarget(null) },
          )
        }
        onCancel={() => setRevokeTarget(null)}
      />
    </>
  );
};
