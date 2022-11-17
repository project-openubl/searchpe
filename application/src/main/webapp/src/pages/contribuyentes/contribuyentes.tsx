import React, { useEffect, useState } from "react";

import { SimplePlaceholder, ConditionalRender } from "@project-openubl/lib-ui";

import { PageSection, ToolbarGroup, ToolbarItem } from "@patternfly/react-core";
import {
  cellWidth,
  IActions,
  ICell,
  IExtraData,
  IRow,
  IRowData,
  sortable,
} from "@patternfly/react-table";

import {
  useModal,
  useTableControls,
  SimpleTableWithToolbar,
} from "@project-openubl/lib-ui";

import {
  IContribuyentesParams,
  IContribuyentesParamsBuilder,
  useContribuyentesQuery,
} from "queries/contribuyentes";
import { SimplePageSection, SearchInput } from "shared/components";

import { Contribuyente, SortByQuery } from "api/models";

import { DetailsModal } from "./components/details-modal/details-modal";

const columns: ICell[] = [
  { title: "RUC", transforms: [cellWidth(20)] },
  { title: "DNI", transforms: [cellWidth(15)] },
  { title: "Nombre", transforms: [sortable, cellWidth(50)] },
  { title: "Estado", transforms: [cellWidth(15)] },
];

const toSortByQuery = (sortBy?: {
  index: number;
  direction: "asc" | "desc";
}): SortByQuery | undefined => {
  if (!sortBy) {
    return undefined;
  }

  let field: string;
  switch (sortBy.index) {
    case 2:
      field = "nombre";
      break;
    default:
      return undefined;
  }

  return {
    orderBy: field,
    orderDirection: sortBy.direction,
  };
};

const CONTRIBUYENTE_FIELD = "contribuyente";

const itemsToRow = (items: Contribuyente[]) => {
  return items.map((item) => ({
    [CONTRIBUYENTE_FIELD]: item,
    cells: [
      {
        title: item.ruc,
      },
      {
        title: item.dni,
      },
      {
        title: item.nombre,
      },
      {
        title: item.estado,
      },
    ],
  }));
};

const getRow = (rowData: IRowData): Contribuyente => {
  return rowData[CONTRIBUYENTE_FIELD];
};

export const Contribuyentes: React.FC = () => {
  const {
    data: modalData,
    open: openModal,
    close: closeModal,
  } = useModal<"view", Contribuyente>();

  const [filterText, setFilterText] = useState("");
  const {
    page: currentPage,
    sortBy: currentSortBy,
    changePage: onPageChange,
    changeSortBy: onChangeSortBy,
  } = useTableControls();

  const [queryParams, setQueryParams] = useState<IContribuyentesParams>(
    new IContribuyentesParamsBuilder()
      .withFilterText(filterText)
      .withPagination(currentPage)
      .withSorting(toSortByQuery(currentSortBy))
      .build()
  );
  const contribuyentes = useContribuyentesQuery(queryParams);

  useEffect(() => {
    const params = new IContribuyentesParamsBuilder()
      .withFilterText(filterText)
      .withPagination(currentPage)
      .withSorting(toSortByQuery(currentSortBy))
      .build();
    setQueryParams(params);
  }, [filterText, currentPage, currentSortBy]);

  const actions: IActions = [
    {
      title: "Ver detalle",
      onClick: (
        event: React.MouseEvent,
        rowIndex: number,
        rowData: IRowData,
        extraData: IExtraData
      ) => {
        const row: Contribuyente = getRow(rowData);
        openModal("view", row);
      },
    },
  ];

  const rows: IRow[] = itemsToRow(contribuyentes.data?.data || []);

  return (
    <>
      <ConditionalRender
        when={!!contribuyentes.data && !(contribuyentes.data || contribuyentes)}
        then={<SimplePlaceholder />}
      >
        <SimplePageSection
          title="Buscar por 'Nombre'"
          description="Ingresa el nombre de la persona natural o jurídica que deseas consultar."
        />
        <PageSection>
          <SimpleTableWithToolbar
            hasTopPagination
            totalCount={contribuyentes.data?.meta.count || 0}
            // Sorting
            sortBy={
              currentSortBy || { index: undefined, defaultDirection: "asc" }
            }
            onSort={onChangeSortBy}
            // Pagination
            currentPage={currentPage}
            onPageChange={onPageChange}
            // Table
            rows={rows}
            cells={columns}
            actions={actions}
            // Fech data
            isLoading={contribuyentes.isFetching}
            loadingVariant="skeleton"
            fetchError={contribuyentes.isError}
            // Toolbar filters
            filtersApplied={filterText.trim().length > 0}
            toolbarToggle={
              <ToolbarGroup variant="filter-group">
                <ToolbarItem>
                  <SearchInput onSearch={setFilterText} />
                </ToolbarItem>
              </ToolbarGroup>
            }
          />
        </PageSection>
      </ConditionalRender>
      <DetailsModal value={modalData} onClose={closeModal} />
    </>
  );
};
