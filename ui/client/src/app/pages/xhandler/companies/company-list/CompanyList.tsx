import type React from "react";

import { Link, useNavigate } from "@tanstack/react-router";

import {
  Content,
  ContentVariants,
  Flex,
  FlexItem,
  Label,
  PageSection,
  PaginationVariant,
} from "@patternfly/react-core";
import {
  DataView,
  DataViewTable,
  DataViewToolbar,
} from "@patternfly/react-data-view";

import { dataViewBodyStates } from "@app/components/dataViewStates";
import { DocumentTitle } from "@app/components/DocumentTitle";
import { GatedButton } from "@app/components/GatedButton";
import { ListPagination } from "@app/components/ListPagination";
import { SunatEnvLabel } from "@app/components/SunatEnvLabel";
import { TableEmptyState } from "@app/components/TableEmptyState";
import { TenantContextSelector } from "@app/components/TenantContextSelector";
import { usePersistentState } from "@app/hooks/usePersistentState";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";
import { useTenantRole } from "@app/hooks/useTenantRole";
import { useCompaniesListQuery } from "@app/queries/xhandler/companies";
import type { DataViewTh } from "@app/utils/dataViewTypes";
import {
  estimateItemCount,
  PAGINATION_LIST_STATE,
  toPageParams,
} from "@app/utils/listState";

/** A tenant's companies (RUCs), the level below the organization (ADR 0002). */
export const CompanyList: React.FC = () => {
  const tenantId = useSelectedTenantId();
  const navigate = useNavigate();
  const { disabledProps } = useTenantRole();
  const [pagination, setPagination] = usePersistentState({
    ...PAGINATION_LIST_STATE.pagination,
    storage: "url",
  });

  const { data, isLoading, error } = useCompaniesListQuery({
    params: toPageParams(pagination),
  });
  const companies = data?.items ?? [];

  const columns: DataViewTh[] = [
    { cell: "RUC", props: { width: 15 } },
    { cell: "Razón social", props: { width: 45 } },
    { cell: "Ambiente SUNAT", props: { width: 15 } },
    { cell: "RUC verificado", props: { width: 25 } },
  ];

  const rows = companies.map((company) => ({
    id: company.id ?? "",
    row: [
      {
        cell: company.id ? (
          <Link
            to="/organizaciones/$tenantId/empresas/$companyId"
            params={{ tenantId, companyId: company.id }}
          >
            {company.ruc}
          </Link>
        ) : (
          company.ruc
        ),
      },
      {
        cell: company.razon_social ?? "-",
        props: { modifier: "truncate" as const },
      },
      { cell: <SunatEnvLabel sunatEnv={company.sunat_env} /> },
      {
        cell: company.ruc_verified_at ? (
          <Label color="green" isCompact>
            Verificado
          </Label>
        ) : (
          <Label color="orange" isCompact>
            Sin verificar
          </Label>
        ),
      },
    ],
  }));

  const pager = (variant: PaginationVariant) => (
    <ListPagination
      variant={variant}
      itemCount={estimateItemCount({ pagination, pageSize: companies.length })}
      isCountEstimated
      pagination={pagination}
      onPaginationChange={setPagination}
    />
  );

  const { activeState, bodyStates } = dataViewBodyStates({
    columnCount: columns.length,
    isLoading,
    error,
    isEmpty: rows.length === 0,
    emptyState: (
      <TableEmptyState
        title="Esta organización aún no tiene empresas"
        body="Registra una empresa con su RUC para empezar a emitir."
      />
    ),
  });

  return (
    <>
      <DocumentTitle title="Empresas" />
      <TenantContextSelector />
      <PageSection>
        <Flex
          justifyContent={{ default: "justifyContentSpaceBetween" }}
          alignItems={{ default: "alignItemsCenter" }}
        >
          <FlexItem>
            <Content component={ContentVariants.h1}>Empresas</Content>
            <Content component={ContentVariants.p}>
              Las empresas (RUC) que emiten comprobantes en esta organización.
            </Content>
          </FlexItem>
          <FlexItem>
            <GatedButton
              variant="primary"
              onClick={() =>
                navigate({
                  to: "/organizaciones/$tenantId/empresas/nueva",
                  params: { tenantId },
                })
              }
              {...disabledProps("admin")}
            >
              Nueva empresa
            </GatedButton>
          </FlexItem>
        </Flex>
      </PageSection>
      <PageSection>
        <DataView activeState={activeState}>
          <DataViewToolbar pagination={pager(PaginationVariant.top)} />
          <DataViewTable
            aria-label="Tabla de empresas"
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
