import React, { useEffect, useState } from "react";

import { PageSection, ToolbarGroup, ToolbarItem } from "@patternfly/react-core";
import { IAction } from "@patternfly/react-table";

import {
  IContribuyentesParams,
  IContribuyentesParamsBuilder,
  useContribuyentesQuery,
} from "queries/contribuyentes";
import {
  ConditionalRender,
  SearchInput,
  SimplePageSection,
  SimplePlaceholder,
  SimpleTableColumn,
  SimpleTableWithToolbar,
} from "shared/components";
import { useModal, useTableControls } from "shared/hooks";
import { SortBy } from "shared/types";

import { Contribuyente, SortByQuery } from "api/models";

import { DetailsModal } from "./components/details-modal/details-modal";

const columns: SimpleTableColumn[] = [
  { title: "RUC", width: 20 },
  { title: "DNI", width: 15 },
  { title: "Nombre", width: 50, isSortable: true },
  { title: "Estado", width: 15 },
];

const toSortByQuery = (sortBy?: SortBy): SortByQuery | undefined => {
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

  const actionResolver = (item: Contribuyente): IAction[] => [
    {
      title: "Ver detalle",
      onClick: () => openModal("view", item),
    },
  ];

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
        <PageSection hasBodyWrapper={false}>
          <SimpleTableWithToolbar
            hasTopPagination
            totalCount={contribuyentes.data?.meta.count || 0}
            // Sorting
            sortBy={currentSortBy}
            onSort={onChangeSortBy}
            // Pagination
            currentPage={currentPage}
            onPageChange={onPageChange}
            // Table
            columns={columns}
            items={contribuyentes.data?.data || []}
            itemKey={(item) => item.ruc}
            renderCells={(item) => [
              item.ruc,
              item.dni,
              item.nombre,
              item.estado,
            ]}
            actionResolver={actionResolver}
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
