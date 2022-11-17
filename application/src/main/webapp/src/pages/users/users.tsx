import React, { useState } from "react";

import { useDispatch } from "react-redux";
import { alertActions } from "store/alert";

import {
  useModal,
  useTableControls,
  useTable,
  SimpleTableWithToolbar,
  SimplePlaceholder,
  ConditionalRender,
  useConfirmationContext,
} from "@project-openubl/lib-ui";

import {
  Button,
  ButtonVariant,
  Modal,
  ModalVariant,
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

import { User } from "api/models";

import { SearchInput, SimplePageSection } from "shared/components";

import { UserForm } from "./components/user-form";
import { getAxiosErrorMessage } from "utils/modelUtils";
import { useDeleteUserMutation, useUsersQuery } from "queries/users";

const columns: ICell[] = [
  { title: "Usuario", transforms: [sortable, cellWidth(30)] },
  { title: "Permisos", transforms: [cellWidth(50)] },
  { title: "Nombre", transforms: [cellWidth(20)] },
];

const USER_FIELD = "user";

const itemsToRow = (items: User[]) => {
  return items.map((item) => ({
    [USER_FIELD]: item,
    cells: [
      {
        title: item.username,
      },
      {
        title: item.permissions?.join(", "),
      },
      {
        title: item.fullName,
      },
    ],
  }));
};

const getRow = (rowData: IRowData): User => {
  return rowData[USER_FIELD];
};

export const compareByColumnIndex = (
  a: User,
  b: User,
  columnIndex?: number
) => {
  switch (columnIndex) {
    case 0: // username
      return a.username.localeCompare(b.username);
    default:
      return 0;
  }
};

export const filterByText = (filterText: string, item: User) => {
  return (
    item.username.toString().toLowerCase().indexOf(filterText.toLowerCase()) !==
    -1
  );
};

export const Users: React.FC = () => {
  const dispatch = useDispatch();
  const confirmation = useConfirmationContext();

  const [filterText, setFilterText] = useState("");

  const usersQuery = useUsersQuery();
  const deleteUserMutation = useDeleteUserMutation();

  const {
    isOpen: isUserModalOpen,
    data: userToUpdate,
    open: openUserModal,
    close: closeUserModal,
  } = useModal<"create" | "update", User>();

  const {
    page: currentPage,
    sortBy: currentSortBy,
    changePage: onPageChange,
    changeSortBy: onChangeSortBy,
  } = useTableControls();

  const { pageItems, filteredItems } = useTable<User>({
    items: usersQuery.data || [],
    currentPage: currentPage,
    currentSortBy: currentSortBy,
    compareToByColumn: compareByColumnIndex,
    filterItem: (item) => filterByText(filterText, item),
  });

  const actionResolver = (rowData: IRowData): (IAction | ISeparator)[] => {
    const actions: (IAction | ISeparator)[] = [];

    actions.push({
      title: "Editar",
      onClick: (
        event: React.MouseEvent,
        rowIndex: number,
        rowData: IRowData,
        extraData: IExtraData
      ) => {
        const row: User = getRow(rowData);
        openUserModal("update", row);
      },
    });

    actions.push({
      title: "Eliminar",
      onClick: (
        event: React.MouseEvent,
        rowIndex: number,
        rowData: IRowData,
        extraData: IExtraData
      ) => {
        const row: User = getRow(rowData);
        confirmation.open({
          title: "Eliminar usuario",
          titleIconVariant: "warning",
          message: `¿Estas seguro de querer eliminar este Usuario? Esta acción eliminará a ${row.username} permanentemente.`,
          confirmBtnLabel: "Eliminar",
          cancelBtnLabel: "Cancelar",
          confirmBtnVariant: ButtonVariant.danger,
          onConfirm: () => {
            confirmation.enableProcessing();
            deleteUserMutation
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

    return actions;
  };

  const areActionsDisabled = (): boolean => {
    return false;
  };

  const rows: IRow[] = itemsToRow(pageItems || []);

  const handleOnUserFormSaved = (user: User) => {
    if (!userToUpdate) {
      dispatch(
        alertActions.addAlert("success", "Éxito", "Usuario actualizado")
      );
    }
    closeUserModal();
  };

  return (
    <>
      <ConditionalRender
        when={usersQuery.isFetching && !(usersQuery.data || usersQuery.isError)}
        then={<SimplePlaceholder />}
      >
        <SimplePageSection title="Usuarios" />
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
            isLoading={usersQuery.isFetching}
            loadingVariant="skeleton"
            fetchError={usersQuery.isError}
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
                    aria-label="new-user"
                    variant={ButtonVariant.primary}
                    onClick={() => openUserModal("create")}
                  >
                    Nuevo usuario
                  </Button>
                </ToolbarItem>
              </ToolbarGroup>
            }
          />
        </PageSection>
      </ConditionalRender>
      <Modal
        title={`${userToUpdate ? "Editar" : "Crear"} usuario`}
        variant={ModalVariant.medium}
        isOpen={isUserModalOpen}
        onClose={closeUserModal}
      >
        <UserForm
          user={userToUpdate}
          onSaved={handleOnUserFormSaved}
          onCancel={closeUserModal}
        />
      </Modal>
    </>
  );
};
