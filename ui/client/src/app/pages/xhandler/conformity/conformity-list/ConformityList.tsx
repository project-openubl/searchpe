import type React from "react";

import { Link } from "@tanstack/react-router";
import dayjs from "dayjs";

import {
  Breadcrumb,
  BreadcrumbItem,
  Button,
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

import { ClearFiltersButton } from "@app/components/ClearFiltersButton";
import {
  CONFORMITY_STATE_PRESENTATION,
  ConformityStateLabel,
} from "@app/components/ConformityStateLabel";
import { dataViewBodyStates } from "@app/components/dataViewStates";
import { DocumentTitle } from "@app/components/DocumentTitle";
import { ListPagination } from "@app/components/ListPagination";
import { SingleSelectFilter } from "@app/components/SingleSelectFilter";
import { TableEmptyState } from "@app/components/TableEmptyState";
import { TenantContextSelector } from "@app/components/TenantContextSelector";
import { RENDER_DATETIME_FORMAT } from "@app/constants";
import { useNotifications } from "@app/hooks/useNotifications";
import { usePersistentState } from "@app/hooks/usePersistentState";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";
import { useSuspenseCompanyDetailQuery } from "@app/queries/xhandler/companies";
import {
  CONFORMITY_STATES,
  useConformityListQuery,
  useExportConformityMutation,
} from "@app/queries/xhandler/conformity";
import { getMutationErrorMessage } from "@app/utils/apiError";
import type { DataViewTh } from "@app/utils/dataViewTypes";
import { estimateItemCount, toPageParams } from "@app/utils/listState";

import { CONFORMITY_LIST_STATE } from "./conformityListState";

interface IConformityListProps {
  companyId: string;
}

const STATE_OPTIONS = CONFORMITY_STATES.map((state) => ({
  value: state,
  label: CONFORMITY_STATE_PRESENTATION[state].label,
}));

const formatTime = (value: string | undefined): string =>
  value ? dayjs(value).format(RENDER_DATETIME_FORMAT) : "-";

/**
 * A company's facturas negociables and their conformity (DESIGN.md §11.8), with the CSV export
 * for factoring.
 */
export const ConformityList: React.FC<IConformityListProps> = ({
  companyId,
}) => {
  const tenantId = useSelectedTenantId();
  const { addNotification } = useNotifications();
  const { data: company } = useSuspenseCompanyDetailQuery({ companyId });

  const [pagination, setPagination] = usePersistentState({
    ...CONFORMITY_LIST_STATE.pagination,
    storage: "url",
  });
  const [filters, setFilters] = usePersistentState({
    ...CONFORMITY_LIST_STATE.filters,
    storage: "url",
  });
  const toFirstPage = () => setPagination((prev) => ({ ...prev, page: 1 }));
  const clearAllFilters = () => {
    setFilters(CONFORMITY_LIST_STATE.filters.defaultValue);
    toFirstPage();
  };

  const exportParams = { state: filters.estado[0] };
  const { data, isLoading, error } = useConformityListQuery({
    companyId,
    params: { ...toPageParams(pagination), ...exportParams },
  });
  const items = data?.items ?? [];

  const exportCsv = useExportConformityMutation({
    onError: (err) =>
      addNotification({
        variant: "danger",
        ...getMutationErrorMessage({
          error: err,
          fallback: "No se pudo exportar las facturas negociables",
        }),
      }),
  });

  const columns: DataViewTh[] = [
    { cell: "Factura", props: { width: 15 } },
    { cell: "RUC del adquirente", props: { width: 15 } },
    { cell: "Conformidad", props: { width: 15 } },
    { cell: "Aceptada por SUNAT", props: { width: 20 } },
    { cell: "Vence el plazo", props: { width: 20 } },
    { cell: "Resuelta", props: { width: 15 } },
  ];

  const rows = items.map((item) => ({
    id: item.document_id ?? item.serie_numero ?? "",
    row: [
      {
        cell: item.document_id ? (
          <Link
            to="/organizaciones/$tenantId/empresas/$companyId/conformidad/$documentId"
            params={{ tenantId, companyId, documentId: item.document_id }}
          >
            {item.serie_numero}
          </Link>
        ) : (
          item.serie_numero
        ),
      },
      { cell: item.buyer_ruc ?? "-" },
      { cell: <ConformityStateLabel state={item.state} /> },
      { cell: formatTime(item.accepted_at) },
      { cell: formatTime(item.window_ends_at) },
      { cell: formatTime(item.resolved_at) },
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
        title="No hay facturas negociables"
        body="Las facturas a crédito que SUNAT acepte para un adquirente con RUC aparecerán aquí."
        isFiltered={isFiltered}
        onClearFilters={clearAllFilters}
      />
    ),
  });

  const pager = (variant: PaginationVariant) => (
    <ListPagination
      variant={variant}
      itemCount={estimateItemCount({ pagination, pageSize: items.length })}
      isCountEstimated
      pagination={pagination}
      onPaginationChange={setPagination}
    />
  );

  const title = company.razon_social || company.ruc || companyId;

  return (
    <>
      <DocumentTitle title={`Facturas negociables - ${title}`} />
      <TenantContextSelector />
      <PageSection>
        <Breadcrumb>
          <BreadcrumbItem>
            <Link to="/organizaciones">Organizaciones</Link>
          </BreadcrumbItem>
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
          <BreadcrumbItem isActive>Facturas negociables</BreadcrumbItem>
        </Breadcrumb>
        <Content component={ContentVariants.h1}>Facturas negociables</Content>
        <Content component={ContentVariants.p}>
          La conformidad de los adquirentes con las facturas a crédito de{" "}
          {title}. El plazo de 8 días es aproximado: SUNAT decide la conformidad
          presunta.
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
                <SingleSelectFilter
                  filterId="estado"
                  title="Conformidad"
                  placeholder="Filtrar por conformidad"
                  options={STATE_OPTIONS}
                />
              </DataViewFilters>
            }
            actions={
              <Button
                variant="secondary"
                isLoading={exportCsv.isPending}
                isDisabled={exportCsv.isPending}
                onClick={() =>
                  exportCsv.mutate({ companyId, params: exportParams })
                }
              >
                Exportar CSV
              </Button>
            }
            pagination={pager(PaginationVariant.top)}
          />
          <DataViewTable
            aria-label="Tabla de facturas negociables"
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
