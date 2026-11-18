import type React from "react";

import { Link } from "@tanstack/react-router";
import dayjs from "dayjs";

import {
  Breadcrumb,
  BreadcrumbItem,
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
import { dataViewBodyStates } from "@app/components/dataViewStates";
import { DocumentTitle } from "@app/components/DocumentTitle";
import { ListPagination } from "@app/components/ListPagination";
import { SingleSelectFilter } from "@app/components/SingleSelectFilter";
import { SireStatusLabel } from "@app/components/SireStatusLabel";
import { SunatEnvLabel } from "@app/components/SunatEnvLabel";
import { TableEmptyState } from "@app/components/TableEmptyState";
import { TenantContextSelector } from "@app/components/TenantContextSelector";
import { RENDER_DATETIME_FORMAT } from "@app/constants";
import { usePersistentState } from "@app/hooks/usePersistentState";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";
import { useSuspenseCompanyDetailQuery } from "@app/queries/xhandler/companies";
import {
  isSireBook,
  SIRE_BOOK_LABELS,
  SIRE_BOOKS,
  useSirePeriodsListQuery,
} from "@app/queries/xhandler/sire";
import type { DataViewTh } from "@app/utils/dataViewTypes";
import { estimateItemCount, toPageParams } from "@app/utils/listState";

import { SIRE_PERIOD_LIST_STATE } from "./sirePeriodListState";

interface ISirePeriodListProps {
  companyId: string;
}

const BOOK_OPTIONS = SIRE_BOOKS.map((book) => ({
  value: book,
  label: SIRE_BOOK_LABELS[book],
}));

/** `AAAAMM` as "enero de 2026". */
const periodLabel = (period: string | undefined): string =>
  period && period.length === 6
    ? dayjs(`${period.slice(0, 4)}-${period.slice(4)}-01`).format(
        "MMMM [de] YYYY",
      )
    : (period ?? "-");

/**
 * The SIRE periods requested for a company in its current environment (DESIGN.md §11.7), the
 * latest first; each opens the period page with its proposal and reconciliation.
 */
export const SirePeriodList: React.FC<ISirePeriodListProps> = ({
  companyId,
}) => {
  const tenantId = useSelectedTenantId();
  const { data: company } = useSuspenseCompanyDetailQuery({ companyId });

  const [pagination, setPagination] = usePersistentState({
    ...SIRE_PERIOD_LIST_STATE.pagination,
    storage: "url",
  });
  const [filters, setFilters] = usePersistentState({
    ...SIRE_PERIOD_LIST_STATE.filters,
    storage: "url",
  });
  const toFirstPage = () => setPagination((prev) => ({ ...prev, page: 1 }));
  const clearAllFilters = () => {
    setFilters(SIRE_PERIOD_LIST_STATE.filters.defaultValue);
    toFirstPage();
  };

  const { data, isLoading, error } = useSirePeriodsListQuery({
    companyId,
    params: { ...toPageParams(pagination), book: filters.libro[0] },
  });
  const items = data?.items ?? [];

  const columns: DataViewTh[] = [
    { cell: "Periodo", props: { width: 20 } },
    { cell: "Registro", props: { width: 20 } },
    { cell: "Estado", props: { width: 25 } },
    { cell: "Ambiente SUNAT", props: { width: 15 } },
    { cell: "Actualizado", props: { width: 20 } },
  ];

  const rows = items.map((item) => ({
    id: item.id ?? `${item.book}-${item.period}`,
    row: [
      {
        cell:
          item.book && isSireBook(item.book) && item.period ? (
            <Link
              to="/organizaciones/$tenantId/empresas/$companyId/sire"
              params={{ tenantId, companyId }}
              search={{ libro: item.book, periodo: item.period }}
            >
              {periodLabel(item.period)}
            </Link>
          ) : (
            periodLabel(item.period)
          ),
      },
      {
        cell:
          item.book && isSireBook(item.book)
            ? SIRE_BOOK_LABELS[item.book]
            : (item.book ?? "-"),
      },
      { cell: <SireStatusLabel status={item.status} /> },
      { cell: <SunatEnvLabel sunatEnv={item.sunat_env} /> },
      {
        cell: item.updated_at
          ? dayjs(item.updated_at).format(RENDER_DATETIME_FORMAT)
          : "-",
      },
    ],
  }));

  const isFiltered = filters.libro.length > 0;
  const { activeState, bodyStates } = dataViewBodyStates({
    columnCount: columns.length,
    isLoading,
    error,
    isEmpty: rows.length === 0,
    emptyState: (
      <TableEmptyState
        title="No hay periodos de SIRE solicitados"
        body="Los periodos aparecen aquí cuando se solicita la propuesta de SUNAT desde la página de SIRE."
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
      <DocumentTitle title={`Periodos de SIRE - ${title}`} />
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
          <BreadcrumbItem>
            <Link
              to="/organizaciones/$tenantId/empresas/$companyId/sire"
              params={{ tenantId, companyId }}
            >
              SIRE
            </Link>
          </BreadcrumbItem>
          <BreadcrumbItem isActive>Periodos</BreadcrumbItem>
        </Breadcrumb>
        <Content component={ContentVariants.h1}>Periodos de SIRE</Content>
        <Content component={ContentVariants.p}>
          Los periodos de los registros de ventas y compras de {title} que se
          solicitaron a SUNAT, en el ambiente actual de la empresa.
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
                  filterId="libro"
                  title="Registro"
                  placeholder="Filtrar por registro"
                  options={BOOK_OPTIONS}
                />
              </DataViewFilters>
            }
            pagination={pager(PaginationVariant.top)}
          />
          <DataViewTable
            aria-label="Tabla de periodos de SIRE"
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
