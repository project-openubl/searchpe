import React, { useState } from "react";

import {
  Button,
  ButtonVariant,
  Label,
  PageSection,
  ToolbarGroup,
  ToolbarItem,
} from "@patternfly/react-core";
import { IAction } from "@patternfly/react-table";

import {
  useCancelVersionMutation,
  useCreateVersionMutation,
  useDeleteVersionMutation,
  useVersionsQuery,
} from "queries/versions";
import {
  SearchInput,
  SimplePageSection,
  SimpleTableColumn,
  SimpleTableWithToolbar,
  VersionStatusIcon,
} from "shared/components";
import { useConfirmationContext, useNotificationContext } from "shared/context";
import { useTable, useTableControls } from "shared/hooks";

import { Version } from "api/models";
import { formatNumber, getAxiosErrorMessage } from "utils/modelUtils";
import { fromNow } from "utils/dateUtils";

const columns: SimpleTableColumn[] = [
  { title: "Id", width: 10, isSortable: true },
  { title: "Creado", width: 20, isSortable: true },
  { title: "Records", width: 20 },
  { title: "Labels", width: 25 },
  { title: "Estado", width: 25 },
];

const renderCells = (item: Version): React.ReactNode[] => [
  `#${item.id}`,
  fromNow(item.createdAt),
  formatNumber(item.records, 0),
  <Label color={item.active ? "green" : "grey"}>
    {item.active ? "Activo" : "Inactivo"}
  </Label>,
  <VersionStatusIcon value={item.status} />,
];

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
  const { pushNotification } = useNotificationContext();
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

  const notifyError = (error: any) => {
    pushNotification({
      variant: "danger",
      title: "Error",
      message: getAxiosErrorMessage(error),
    });
  };

  const actionResolver = (row: Version): IAction[] => {
    const actions: IAction[] = [];

    if (
      row.status !== "COMPLETED" &&
      row.status !== "ERROR" &&
      row.status !== "CANCELLING" &&
      row.status !== "CANCELLED"
    ) {
      actions.push({
        title: "Cancelar",
        onClick: () => {
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
                .catch(notifyError)
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
        onClick: () => {
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
                .catch(notifyError)
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

  const areActionsDisabled = (row: Version): boolean => {
    return row.status === "DELETING" || row.status === "CANCELLING";
  };

  const onNewVersion = () => {
    createVersionMutation.mutateAsync().catch(notifyError);
  };

  return (
    <>
      <SimplePageSection
        title="Versiones"
        description="Las Versiones representan una versión específica del 'padrón reducido' de la SUNAT."
      />
      <PageSection hasBodyWrapper={false}>
        <SimpleTableWithToolbar
          hasTopPagination
          hasBottomPagination
          totalCount={filteredItems.length}
          // Sorting
          sortBy={currentSortBy}
          onSort={onChangeSortBy}
          // Pagination
          currentPage={currentPage}
          onPageChange={onPageChange}
          // Table
          columns={columns}
          items={pageItems}
          itemKey={(item) => item.id}
          renderCells={renderCells}
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
            <ToolbarGroup variant="action-group">
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
