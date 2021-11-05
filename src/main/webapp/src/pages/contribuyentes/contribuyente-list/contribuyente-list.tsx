import React, { useCallback, useEffect, useState } from "react";
import { RouteComponentProps, useHistory } from "react-router-dom";

import {
  Bullseye,
  EmptyState,
  EmptyStateBody,
  EmptyStateIcon,
  EmptyStateVariant,
  PageSection,
  Title,
} from "@patternfly/react-core";
import {
  IActions,
  ICell,
  IExtraColumnData,
  IExtraData,
  IRowData,
  sortable,
  SortByDirection,
} from "@patternfly/react-table";
import { AddCircleOIcon } from "@patternfly/react-icons";

import { Contribuyente, PageQuery, SortByQuery } from "api/models";

import {
  Welcome,
  AppPlaceholder,
  AppTableWithControls,
  ConditionalRender,
  SimplePageSection,
} from "shared/components";
import { useFetchContribuyentes, useTableControls } from "shared/hooks";

import { Paths } from "Paths";

import { DetailsModal } from "./components/details-modal/details-modal";

const columns: ICell[] = [
  { title: "RUC" },
  { title: "Razón social", transforms: [sortable] },
  { title: "Estado contribuyente" },
];

const columnIndexToField = (
  _: React.MouseEvent,
  index: number,
  direction: SortByDirection,
  extraData: IExtraColumnData
) => {
  switch (index) {
    case 1:
      return "razonSocial";
    default:
      throw new Error("Invalid column index=" + index);
  }
};

const CONTRIBUYENTE_FIELD = "contribuyente";

const getRow = (rowData: IRowData): Contribuyente => {
  return rowData[CONTRIBUYENTE_FIELD];
};

const itemsToRow = (items: Contribuyente[]) => {
  return items.map((item) => ({
    [CONTRIBUYENTE_FIELD]: item,
    cells: [
      {
        title: item.ruc,
      },
      {
        title: item.razonSocial,
      },
      {
        title: item.estadoContribuyente,
      },
    ],
  }));
};

export interface ContribuyenteListProps extends RouteComponentProps {}

export const ContribuyenteList: React.FC<ContribuyenteListProps> = () => {
  const history = useHistory();

  const [currentRow, setCurrentRow] = useState<Contribuyente>();

  const {
    contribuyentes,
    isFetching,
    fetchError,
    fetchCount,
    fetchContribuyentes,
  } = useFetchContribuyentes(true);

  const {
    filterText,
    paginationQuery,
    sortByQuery,
    sortBy,
    handleFilterTextChange,
    handlePaginationChange,
    handleSortChange,
  } = useTableControls({ columnToField: columnIndexToField });

  const reloadTable = useCallback(
    (filterText: string, pagination: PageQuery, sortBy?: SortByQuery) => {
      fetchContribuyentes(pagination, sortBy, filterText);
    },
    [fetchContribuyentes]
  );

  useEffect(() => {
    reloadTable(filterText, paginationQuery, sortByQuery);
  }, [filterText, paginationQuery, sortByQuery, reloadTable]);

  const actions: IActions = [
    {
      title: "View details",
      onClick: (
        event: React.MouseEvent,
        rowIndex: number,
        rowData: IRowData,
        extraData: IExtraData
      ) => {
        const row: Contribuyente = getRow(rowData);
        setCurrentRow(row);
      },
    },
  ];

  const handleOnDetailsModalClose = () => {
    setCurrentRow(undefined);
  };

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
        then={<AppPlaceholder />}
      >
        <SimplePageSection
          title="Search by 'Razón social'"
          description="Write the name of the entity you are searching for and then press enter."
        />
        <PageSection>
          <AppTableWithControls
            count={contribuyentes ? contribuyentes.meta.count : 0}
            items={contribuyentes ? contribuyentes.data : []}
            hiddeBottomPagination={true}
            itemsToRow={itemsToRow}
            pagination={paginationQuery}
            sortBy={sortBy}
            handleFilterTextChange={handleFilterTextChange}
            handlePaginationChange={handlePaginationChange}
            handleSortChange={handleSortChange}
            columns={columns}
            actions={actions}
            isLoading={isFetching}
            loadingVariant="skeleton"
            fetchError={fetchError}
            filtersApplied={filterText.trim().length > 0}
            noDataState={
              <EmptyState variant={EmptyStateVariant.small}>
                <EmptyStateIcon icon={AddCircleOIcon} />
                <Title headingLevel="h2" size="lg">
                  No entities available
                </Title>
                <EmptyStateBody>
                  Start importing entities going to the{" "}
                  <strong>Versions</strong> menu.
                </EmptyStateBody>
              </EmptyState>
            }
          />
        </PageSection>
      </ConditionalRender>
      <DetailsModal value={currentRow} onClose={handleOnDetailsModalClose} />
    </>
  );
};
