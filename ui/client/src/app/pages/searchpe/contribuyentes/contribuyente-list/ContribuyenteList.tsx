import type React from "react";

import { Link } from "@tanstack/react-router";

import {
  Alert,
  Content,
  ContentVariants,
  PageSection,
  PaginationVariant,
} from "@patternfly/react-core";
import {
  DataView,
  DataViewCheckboxFilter,
  DataViewFilters,
  DataViewTable,
  DataViewTextFilter,
  DataViewToolbar,
} from "@patternfly/react-data-view";

import { ClearFiltersButton } from "@app/components/ClearFiltersButton";
import { dataViewBodyStates } from "@app/components/dataViewStates";
import { DocumentTitle } from "@app/components/DocumentTitle";
import { EstadoContribuyenteLabel } from "@app/components/EstadoContribuyenteLabel";
import { ListPagination } from "@app/components/ListPagination";
import { TableEmptyState } from "@app/components/TableEmptyState";
import { UnauthorizedState } from "@app/components/UnauthorizedState";
import { useDebouncedValue } from "@app/hooks/useDebouncedValue";
import { usePersistentState } from "@app/hooks/usePersistentState";
import { useSearchpePermissions } from "@app/hooks/useSearchpePermissions";
import {
  CONTRIBUYENTES_MAX_OFFSET,
  useContribuyentesListQuery,
} from "@app/queries/searchpe/contribuyentes";
import { describeApiError } from "@app/utils/apiError";
import type { DataViewTh } from "@app/utils/dataViewTypes";
import { isHttpError } from "@app/utils/isHttpError";
import { toPageParams } from "@app/utils/listState";
import { columnSort } from "@app/utils/sortHelpers";

import {
  CONTRIBUYENTE_LIST_STATE,
  SORTABLE_COLUMN_KEYS,
} from "./contribuyenteListState";

const TIPO_PERSONA_OPTIONS = [
  { value: "NATURAL", label: "Persona natural" },
  { value: "JURIDICA", label: "Persona jurídica" },
];

/** The active padrón version, searched by name, RUC or DNI (searchpe's advanced search). */
export const ContribuyenteList: React.FC = () => {
  const { can } = useSearchpePermissions();

  const [pagination, setPagination] = usePersistentState({
    ...CONTRIBUYENTE_LIST_STATE.pagination,
    storage: "url",
  });
  const [sort, setSort] = usePersistentState({
    ...CONTRIBUYENTE_LIST_STATE.sort,
    storage: "url",
  });
  const [filters, setFilters] = usePersistentState({
    ...CONTRIBUYENTE_LIST_STATE.filters,
    storage: "url",
  });

  const toFirstPage = () => setPagination((prev) => ({ ...prev, page: 1 }));
  const clearAllFilters = () => {
    setFilters(CONTRIBUYENTE_LIST_STATE.filters.defaultValue);
    toFirstPage();
  };

  const { sortByParam, sortProps } = columnSort({
    columnKeys: SORTABLE_COLUMN_KEYS,
    sort,
    onSort: (next) => {
      setSort(next);
      toFirstPage();
    },
  });

  const debouncedTexto = useDebouncedValue({ value: filters.texto.trim() });
  // One type filters the list; both (or none) is every contribuyente.
  const tipoPersona =
    filters.tipoPersona.length === 1 ? filters.tipoPersona[0] : undefined;

  const pageParams = toPageParams(pagination);
  const isBeyondMaxOffset = pageParams.offset > CONTRIBUYENTES_MAX_OFFSET;

  const { data, isLoading, error } = useContribuyentesListQuery({
    params: {
      ...pageParams,
      filterText: debouncedTexto || undefined,
      tipoPersona,
      sort_by: sortByParam ? [sortByParam] : undefined,
    },
    options: { enabled: can("search") && !isBeyondMaxOffset },
  });

  const isFiltered = Boolean(debouncedTexto) || filters.tipoPersona.length > 0;
  // A 400 means the server has the advanced search turned off.
  const isSearchDisabled = isHttpError({ error, status: 400 });

  const columns: DataViewTh[] = [
    { cell: "RUC", props: { width: 15 } },
    { cell: "DNI", props: { width: 10 } },
    {
      cell: "Nombre o razón social",
      props: { sort: sortProps("nombre"), width: 60 },
    },
    { cell: "Estado", props: { width: 15 } },
  ];

  const rows = (data?.data ?? []).map((contribuyente) => ({
    id: contribuyente.ruc ?? "",
    row: [
      {
        cell: contribuyente.ruc ? (
          <Link
            to="/padron/contribuyentes/$numeroDocumento"
            params={{ numeroDocumento: contribuyente.ruc }}
          >
            {contribuyente.ruc}
          </Link>
        ) : (
          "-"
        ),
      },
      { cell: contribuyente.dni || "-" },
      {
        cell: contribuyente.nombre ?? "-",
        props: { modifier: "truncate" as const },
      },
      { cell: <EstadoContribuyenteLabel estado={contribuyente.estado} /> },
    ],
  }));

  const { activeState, bodyStates } = dataViewBodyStates({
    columnCount: columns.length,
    isLoading,
    error: isSearchDisabled ? null : error,
    isEmpty: rows.length === 0,
    emptyState: (
      <TableEmptyState
        title="No hay contribuyentes"
        body="El padrón aún no tiene una versión activa."
        isFiltered={isFiltered}
        onClearFilters={clearAllFilters}
      />
    ),
  });

  return (
    <>
      <DocumentTitle title="Contribuyentes" />
      <PageSection>
        <Content component={ContentVariants.h1}>Contribuyentes</Content>
        <Content component={ContentVariants.p}>
          Contribuyentes de la versión activa del padrón reducido de la SUNAT.
        </Content>
      </PageSection>
      <PageSection>
        {!can("search") ? (
          <UnauthorizedState description="Necesitas el permiso search en searchpe para ver los contribuyentes." />
        ) : isSearchDisabled ? (
          <Alert
            variant="info"
            isInline
            title="La búsqueda de contribuyentes está desactivada"
          >
            {describeApiError(error?.response?.data).message ??
              "El servidor no permite buscar en el padrón."}{" "}
            Usa la Consulta RUC para buscar por número de documento.
          </Alert>
        ) : (
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
                    filterId="texto"
                    title="Nombre, RUC o DNI"
                    placeholder="Filtrar por nombre, RUC o DNI"
                  />
                  <DataViewCheckboxFilter
                    filterId="tipoPersona"
                    title="Tipo de persona"
                    placeholder="Filtrar por tipo de persona"
                    options={TIPO_PERSONA_OPTIONS}
                  />
                </DataViewFilters>
              }
              pagination={
                <ListPagination
                  variant={PaginationVariant.top}
                  itemCount={data?.meta?.count ?? 0}
                  pagination={pagination}
                  onPaginationChange={setPagination}
                />
              }
            />
            <DataViewTable
              aria-label="Tabla de contribuyentes"
              columns={columns}
              rows={rows}
              bodyStates={bodyStates}
            />
            <DataViewToolbar
              pagination={
                <ListPagination
                  variant={PaginationVariant.bottom}
                  itemCount={data?.meta?.count ?? 0}
                  pagination={pagination}
                  onPaginationChange={setPagination}
                />
              }
            />
          </DataView>
        )}
        {isBeyondMaxOffset ? (
          <Alert variant="warning" isInline title="Página fuera de rango">
            searchpe muestra hasta los primeros{" "}
            {CONTRIBUYENTES_MAX_OFFSET.toLocaleString("es-PE")} resultados.
            Afina los filtros para encontrar el contribuyente.
          </Alert>
        ) : null}
      </PageSection>
    </>
  );
};
