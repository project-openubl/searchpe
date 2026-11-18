import type React from "react";

import { Link } from "@tanstack/react-router";
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

import { ClearFiltersButton } from "@app/components/ClearFiltersButton";
import { dataViewBodyStates } from "@app/components/dataViewStates";
import { DateFilter } from "@app/components/DateFilter";
import {
  DOCUMENT_STATUS_PRESENTATION,
  DocumentStatusLabel,
} from "@app/components/DocumentStatusLabel";
import { DocumentTitle } from "@app/components/DocumentTitle";
import { ListPagination } from "@app/components/ListPagination";
import { SingleSelectFilter } from "@app/components/SingleSelectFilter";
import { TableEmptyState } from "@app/components/TableEmptyState";
import { TenantContextSelector } from "@app/components/TenantContextSelector";
import { DEFAULT_REFETCH_INTERVAL, RENDER_DATE_FORMAT } from "@app/constants";
import { useDebouncedValue } from "@app/hooks/useDebouncedValue";
import { usePersistentState } from "@app/hooks/usePersistentState";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";
import {
  DOCUMENT_STATUSES,
  isDocumentSettling,
  TIPOS_COMPROBANTE,
  useDocumentsListQuery,
} from "@app/queries/xhandler/documents";
import type { DataViewTh } from "@app/utils/dataViewTypes";
import {
  formatTotal,
  TIPO_COMPROBANTE_LABELS,
  tipoComprobanteLabel,
} from "@app/utils/documentPresentation";
import { estimateItemCount, toPageParams } from "@app/utils/listState";

import { DOCUMENT_LIST_STATE } from "./documentListState";

const TIPO_OPTIONS = TIPOS_COMPROBANTE.map((tipo) => ({
  value: tipo,
  label: `${TIPO_COMPROBANTE_LABELS[tipo]} (${tipo})`,
}));

const STATUS_OPTIONS = DOCUMENT_STATUSES.map((status) => ({
  value: status,
  label: DOCUMENT_STATUS_PRESENTATION[status].label,
}));

/** The tenant's issued documents, newest first, filtered server-side (DESIGN.md §12). */
export const DocumentList: React.FC = () => {
  const tenantId = useSelectedTenantId();
  const [pagination, setPagination] = usePersistentState({
    ...DOCUMENT_LIST_STATE.pagination,
    storage: "url",
  });
  const [filters, setFilters] = usePersistentState({
    ...DOCUMENT_LIST_STATE.filters,
    storage: "url",
  });
  const toFirstPage = () => setPagination((prev) => ({ ...prev, page: 1 }));
  const clearAllFilters = () => {
    setFilters(DOCUMENT_LIST_STATE.filters.defaultValue);
    toFirstPage();
  };

  const debouncedSerie = useDebouncedValue({
    value: filters.serie.trim().toUpperCase(),
  });

  const { data, isLoading, error } = useDocumentsListQuery({
    params: {
      ...toPageParams(pagination),
      tipo_comprobante: filters.tipo[0],
      status: filters.estado[0],
      serie: debouncedSerie || undefined,
      from: filters.desde || undefined,
      to: filters.hasta || undefined,
    },
    options: {
      // Server-derived status: poll while a document on the page waits for SUNAT.
      refetchInterval: (query) =>
        query.state.data?.items.some((document) =>
          isDocumentSettling(document.status),
        )
          ? DEFAULT_REFETCH_INTERVAL
          : false,
    },
  });
  const documents = data?.items ?? [];

  const columns: DataViewTh[] = [
    { cell: "Comprobante", props: { width: 15 } },
    { cell: "Tipo", props: { width: 15 } },
    { cell: "Emisión", props: { width: 10 } },
    { cell: "Cliente", props: { width: 25 } },
    { cell: "Total", props: { width: 10 } },
    { cell: "Estado", props: { width: 25 } },
  ];

  const rows = documents.map((document) => ({
    id: document.id ?? "",
    row: [
      {
        cell: document.id ? (
          <Link
            to="/organizaciones/$tenantId/documentos/$documentId"
            params={{ tenantId, documentId: document.id }}
          >
            {document.serie_numero ?? document.id}
          </Link>
        ) : (
          (document.serie_numero ?? "-")
        ),
      },
      {
        cell: tipoComprobanteLabel(document.tipo_comprobante),
        props: { modifier: "truncate" as const },
      },
      {
        cell: document.fecha_emision
          ? dayjs(document.fecha_emision).format(RENDER_DATE_FORMAT)
          : "-",
      },
      {
        cell: document.customer?.nombre
          ? `${document.customer.nombre}${document.customer.numero ? ` (${document.customer.numero})` : ""}`
          : (document.customer?.numero ?? "-"),
        props: { modifier: "truncate" as const },
      },
      {
        cell: formatTotal({ total: document.total, moneda: document.moneda }),
      },
      {
        cell: (
          <DocumentStatusLabel
            status={document.status}
            holdReason={document.hold_reason}
          />
        ),
      },
    ],
  }));

  const isFiltered =
    filters.tipo.length > 0 ||
    filters.estado.length > 0 ||
    Boolean(debouncedSerie) ||
    Boolean(filters.desde) ||
    Boolean(filters.hasta);

  const { activeState, bodyStates } = dataViewBodyStates({
    columnCount: columns.length,
    isLoading,
    error,
    isEmpty: rows.length === 0,
    emptyState: (
      <TableEmptyState
        title="No hay comprobantes"
        body="Los comprobantes que emitan las empresas de esta organización aparecerán aquí."
        isFiltered={isFiltered}
        onClearFilters={clearAllFilters}
      />
    ),
  });

  const pager = (variant: PaginationVariant) => (
    <ListPagination
      variant={variant}
      itemCount={estimateItemCount({ pagination, pageSize: documents.length })}
      isCountEstimated
      pagination={pagination}
      onPaginationChange={setPagination}
    />
  );

  return (
    <>
      <DocumentTitle title="Comprobantes" />
      <TenantContextSelector />
      <PageSection>
        <Content component={ContentVariants.h1}>Comprobantes</Content>
        <Content component={ContentVariants.p}>
          Los comprobantes emitidos por las empresas de esta organización y su
          estado en SUNAT.
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
                  filterId="serie"
                  title="Serie"
                  placeholder="Filtrar por serie (F001)"
                />
                <SingleSelectFilter
                  filterId="tipo"
                  title="Tipo"
                  placeholder="Filtrar por tipo"
                  options={TIPO_OPTIONS}
                />
                <SingleSelectFilter
                  filterId="estado"
                  title="Estado"
                  placeholder="Filtrar por estado"
                  options={STATUS_OPTIONS}
                />
                <DateFilter
                  filterId="desde"
                  title="Emitido desde"
                  placeholder="Emitido desde"
                />
                <DateFilter
                  filterId="hasta"
                  title="Emitido hasta"
                  placeholder="Emitido hasta"
                />
              </DataViewFilters>
            }
            pagination={pager(PaginationVariant.top)}
          />
          <DataViewTable
            aria-label="Tabla de comprobantes"
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
