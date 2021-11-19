import React, { useCallback, useEffect, useState } from "react";
import { useHistory } from "react-router-dom";

import { SimplePlaceholder } from "@project-openubl/lib-ui";

import {
  Bullseye,
  PageSection,
  ToolbarGroup,
  ToolbarItem,
} from "@patternfly/react-core";
import {
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

import { Contribuyente, SortByQuery } from "api/models";

import {
  Welcome,
  ConditionalRender,
  SimplePageSection,
  SearchInput,
} from "shared/components";
import { useFetchContribuyentes } from "shared/hooks";

import { Paths } from "Paths";

import { DetailsModal } from "./components/details-modal/details-modal";

const columns: ICell[] = [
  { title: "Número documento" },
  { title: "Nombre", transforms: [sortable] },
  { title: "Estado" },
  { title: "Tipo persona" },
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
    case 1:
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
        title: item.numeroDocumento,
      },
      {
        title: item.nombre,
      },
      {
        title: item.estado,
      },
      {
        title: item.tipoPersona,
      },
    ],
  }));
};

const getRow = (rowData: IRowData): Contribuyente => {
  return rowData[CONTRIBUYENTE_FIELD];
};

export const ContribuyenteList: React.FC = () => {
  const history = useHistory();
  const [filterText, setFilterText] = useState("");

  const {
    data: modalData,
    open: openModal,
    close: closeModal,
  } = useModal<Contribuyente>();

  const {
    contribuyentes,
    isFetching,
    fetchError,
    fetchCount,
    fetchContribuyentes,
  } = useFetchContribuyentes(true);

  const {
    page: currentPage,
    sortBy: currentSortBy,
    changePage: onPageChange,
    changeSortBy: onChangeSortBy,
  } = useTableControls();

  const reloadTable = useCallback(() => {
    fetchContribuyentes(currentPage, toSortByQuery(currentSortBy), filterText);
  }, [filterText, currentPage, currentSortBy, fetchContribuyentes]);

  useEffect(() => {
    reloadTable();
  }, [reloadTable]);

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
        openModal(row);
      },
    },
  ];

  const rows: IRow[] = itemsToRow(contribuyentes?.data || []);

  const handleOnWelcomePrimaryAction = () => {
    history.push(Paths.versionList);
  };

  if (
    fetchCount === 1 &&
    (contribuyentes?.data === undefined || contribuyentes?.data.length === 0)
  ) {
    return (
      <Bullseye>
        <Welcome onPrimaryAction={handleOnWelcomePrimaryAction} />
      </Bullseye>
    );
  }

  return (
    <>
      <ConditionalRender
        when={isFetching && !(contribuyentes || fetchError)}
        then={<SimplePlaceholder />}
      >
        <SimplePageSection
          title="Buscar por 'Nombre'"
          description="Ingresa el nombre de la persona natural o jurídica que deseas consultar."
        />
        <PageSection>
          <SimpleTableWithToolbar
            hasTopPagination
            totalCount={contribuyentes ? contribuyentes.meta.count : 0}
            // Sorting
            sortBy={currentSortBy}
            onSort={onChangeSortBy}
            // Pagination
            currentPage={currentPage}
            onPageChange={onPageChange}
            // Table
            rows={rows}
            cells={columns}
            actions={actions}
            // Fech data
            isLoading={isFetching}
            loadingVariant="skeleton"
            fetchError={fetchError}
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
