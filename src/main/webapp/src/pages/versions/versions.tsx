import React, { useState } from "react";

import {
  useTable,
  useTableControls,
  SimpleTableWithToolbar,
  useConfirmationContext,
} from "@project-openubl/lib-ui";

import {
  Button,
  ButtonVariant,
  Label,
  PageSection,
  ToolbarGroup,
  ToolbarItem,
} from "@patternfly/react-core";
import {
  cellWidth,
  IAction,
  ICell,
  IExtraData,
  IRow,
  IRowData,
  ISeparator,
  sortable,
} from "@patternfly/react-table";

import {
  useCancelVersionMutation,
  useCreateVersionMutation,
  useDeleteVersionMutation,
  useVersionsQuery,
} from "queries/versions";
import {
  SearchInput,
  SimplePageSection,
  VersionStatusIcon,
} from "shared/components";

import { useDispatch } from "react-redux";
import { alertActions } from "store/alert";

import { Version } from "api/models";
import { formatNumber, getAxiosErrorMessage } from "utils/modelUtils";
import { fromNow } from "utils/dateUtils";

const columns: ICell[] = [
  { title: "Id", transforms: [sortable, cellWidth(10)] },
  { title: "Creado", transforms: [sortable, cellWidth(20)] },
  { title: "Records", transforms: [cellWidth(20)] },
  { title: "Labels", transforms: [cellWidth(25)] },
  { title: "Estado", transforms: [cellWidth(25)] },
];

const VERSION_FIELD = "version";

const itemsToRow = (items: Version[]) => {
  return items.map((item) => ({
    [VERSION_FIELD]: item,
    cells: [
      {
        title: `#${item.id}`,
      },
      {
        title: fromNow(item.createdAt),
      },
      {
        title: formatNumber(item.records, 0),
      },
      {
        title: (
          <Label color={item.active ? "green" : "grey"}>
            {item.active ? "Activo" : "Inactivo"}
          </Label>
        ),
      },
      {
        title: <VersionStatusIcon value={item.status} />,
      },
    ],
  }));
};

const getRow = (rowData: IRowData): Version => {
  return rowData[VERSION_FIELD];
};

export const compareByColumnIndex = (
  a: Version,
  b: Version,
  columnIndex?: number
) => {
  switch (columnIndex) {
    case 0: // id
      return a.id - b.id;
    case 1: // createdAt
      return new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
    default:
      return 0;
  }
};

export const filterByText = (filterText: string, item: Version) => {
  return (
    item.id.toString().toLowerCase().indexOf(filterText.toLowerCase()) !== -1
  );
};

export const Versions: React.FC = () => {
  const dispatch = useDispatch();
  const confirmation = useConfirmationContext();

  const versions = useVersionsQuery();
  const createVersionMutation = useCreateVersionMutation();
  const deleteVersionMutation = useDeleteVersionMutation();
  const cancelVersionMutation = useCancelVersionMutation();

  const [filterText, setFilterText] = useState("");

  const {
    page: currentPage,
    sortBy: currentSortBy,
    changePage: onPageChange,
    changeSortBy: onChangeSortBy,
  } = useTableControls();

  const { pageItems, filteredItems } = useTable<Version>({
    items: versions.data || [],
    currentPage: currentPage,
    currentSortBy: currentSortBy,
    compareToByColumn: compareByColumnIndex,
    filterItem: (item) => filterByText(filterText, item),
  });

  const actionResolver = (rowData: IRowData): (IAction | ISeparator)[] => {
    const row: Version = getRow(rowData);

    const actions: (IAction | ISeparator)[] = [];

    if (
      row.status !== "COMPLETED" &&
      row.status !== "ERROR" &&
      row.status !== "CANCELLING" &&
      row.status !== "CANCELLED"
    ) {
      actions.push({
        title: "Cancelar",
        onClick: (_, rowIndex: number, rowData: IRowData) => {
          const row: Version = getRow(rowData);
          confirmation.open({
            title: "Cancelar Versión",
            titleIconVariant: "warning",
            message: `¿Estas seguro de querer cancelar la creación de esta Versión?`,
            confirmBtnLabel: "Cancelar",
            cancelBtnLabel: "Cerrar",
            confirmBtnVariant: ButtonVariant.primary,
            onConfirm: () => {
              confirmation.enableProcessing();
              cancelVersionMutation
                .mutateAsync(row)
                .catch((error) => {
                  dispatch(
                    alertActions.addAlert(
                      "danger",
                      "Error",
                      getAxiosErrorMessage(error)
                    )
                  );
                })
                .finally(() => {
                  confirmation.close();
                });
            },
          });
        },
      });
    } else {
      actions.push({
        title: "Eliminar",
        onClick: (
          event: React.MouseEvent,
          rowIndex: number,
          rowData: IRowData,
          extraData: IExtraData
        ) => {
          const row: Version = getRow(rowData);
          confirmation.open({
            title: "Eliminar Versión",
            titleIconVariant: "warning",
            message: `¿Estas seguro de querer eliminar esta Versión? Esta acción eliminará #${row.id} permanentemente.`,
            confirmBtnLabel: "Eliminar",
            cancelBtnLabel: "Cancelar",
            confirmBtnVariant: ButtonVariant.danger,
            onConfirm: () => {
              confirmation.enableProcessing();
              deleteVersionMutation
                .mutateAsync(row)
                .catch((error) => {
                  dispatch(
                    alertActions.addAlert(
                      "danger",
                      "Error",
                      getAxiosErrorMessage(error)
                    )
                  );
                })
                .finally(() => {
                  confirmation.close();
                });
            },
          });
        },
      });
    }

    return actions;
  };

  const areActionsDisabled = (
    rowData: IRowData,
    extraData: IExtraData
  ): boolean => {
    const row: Version = getRow(rowData);
    return row.status === "DELETING" || row.status === "CANCELLING";
  };

  const rows: IRow[] = itemsToRow(pageItems || []);

  const onNewVersion = () => {
    createVersionMutation.mutateAsync().catch((error) => {
      dispatch(
        alertActions.addAlert("danger", "Error", getAxiosErrorMessage(error))
      );
    });
  };

  return (
    <>
      <SimplePageSection
        title="Versiones"
        description="Las Versiones representan una versión específica del 'padrón reducido' de la SUNAT."
      />
      <PageSection>
        <SimpleTableWithToolbar
          hasTopPagination
          hasBottomPagination
          totalCount={filteredItems.length}
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
          actionResolver={actionResolver}
          areActionsDisabled={areActionsDisabled}
          // Fech data
          isLoading={versions.isFetching}
          loadingVariant="none"
          fetchError={versions.isError}
          // Toolbar filters
          filtersApplied={filterText.trim().length > 0}
          toolbarToggle={
            <ToolbarGroup variant="filter-group">
              <ToolbarItem>
                <SearchInput onSearch={setFilterText} />
              </ToolbarItem>
            </ToolbarGroup>
          }
          toolbarActions={
            <ToolbarGroup variant="button-group">
              <ToolbarItem>
                <Button
                  type="button"
                  aria-label="new-version"
                  variant={ButtonVariant.primary}
                  onClick={onNewVersion}
                >
                  Nueva versión
                </Button>
              </ToolbarItem>
            </ToolbarGroup>
          }
        />
      </PageSection>
    </>
  );
};
