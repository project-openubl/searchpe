import React, { useEffect } from "react";
import { RouteComponentProps } from "react-router-dom";
import { AxiosResponse } from "axios";

import { useDispatch } from "react-redux";
import { deleteDialogActions } from "store/deleteDialog";
import { alertActions } from "store/alert";

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
  IExtraColumnData,
  IExtraData,
  IRowData,
  ISeparator,
  sortable,
  SortByDirection,
} from "@patternfly/react-table";

import { User } from "api/models";

import {
  AppPlaceholder,
  AppTableWithControls,
  ConditionalRender,
  SimplePageSection,
} from "shared/components";
import {
  useDeleteUser,
  useEntityModal,
  useFetchUsers,
  useTableControls,
  useTableControlsOffline,
} from "shared/hooks";

import { UserForm } from "./components/user-form";
import { getAxiosErrorMessage } from "utils/modelUtils";
import { Permission } from "Constants";

const columns: ICell[] = [
  { title: "Username", transforms: [sortable, cellWidth(40)] },
  { title: "Role", transforms: [sortable, cellWidth(60)] },
];

const columnIndexToField = (
  _: React.MouseEvent,
  index: number,
  direction: SortByDirection,
  extraData: IExtraColumnData
) => {
  switch (index) {
    case 0:
      return "username";
    case 1:
      return "password";
    default:
      throw new Error("Invalid column index=" + index);
  }
};

const USER_FIELD = "user";

const getRow = (rowData: IRowData): User => {
  return rowData[USER_FIELD];
};

const itemsToRow = (items: User[]) => {
  return items.map((item) => ({
    [USER_FIELD]: item,
    cells: [
      {
        title: item.username,
      },
      {
        title: item.role,
      },
    ],
  }));
};

export const compareByColumnIndex = (
  a: User,
  b: User,
  columnIndex?: number
) => {
  switch (columnIndex) {
    case 0: // username
      return a.username.localeCompare(b.username);
    case 1: // role
      return a.role.localeCompare(b.role);
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

export interface UserListProps extends RouteComponentProps {}

export const UserList: React.FC<UserListProps> = () => {
  const dispatch = useDispatch();

  const { deleteUser } = useDeleteUser();

  const {
    isOpen: isUserModalOpen,
    data: userToUpdate,
    create: openCreateUserModal,
    update: openUpdateUserModal,
    close: closeUserModal,
  } = useEntityModal<User>();

  const { users, isFetching, fetchError, fetchUsers } = useFetchUsers(true);

  const {
    filterText,
    paginationQuery,
    sortBy,
    handleFilterTextChange,
    handlePaginationChange,
    handleSortChange,
  } = useTableControls({ columnToField: columnIndexToField });

  const { pageItems, filteredItems } = useTableControlsOffline({
    items: users || [],
    filterText: filterText,
    paginationQuery: paginationQuery,
    sortBy: sortBy,
    compareByColumnIndex: compareByColumnIndex,
    filterByText: filterByText,
  });

  useEffect(() => {
    fetchUsers();
  }, [fetchUsers]);

  const actionResolver = (rowData: IRowData): (IAction | ISeparator)[] => {
    const row: User = getRow(rowData);

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
        openUpdateUserModal(row);
      },
    });

    if (!row.role.split(",").some((f) => f === Permission.admin)) {
      actions.push({
        title: "Delete",
        onClick: (
          event: React.MouseEvent,
          rowIndex: number,
          rowData: IRowData,
          extraData: IExtraData
        ) => {
          const row: User = getRow(rowData);
          dispatch(
            deleteDialogActions.openModal({
              name: `${row.username}`,
              type: "usuario",
              onDelete: () => {
                dispatch(deleteDialogActions.processing());
                deleteUser(
                  row,
                  () => {
                    dispatch(deleteDialogActions.closeModal());
                    fetchUsers();
                  },
                  (error) => {
                    dispatch(deleteDialogActions.closeModal());
                    dispatch(
                      alertActions.addAlert(
                        "danger",
                        "Error",
                        getAxiosErrorMessage(error)
                      )
                    );
                  }
                );
              },
            })
          );
        },
      });
    }

    return actions;
  };

  const areActionsDisabled = (): boolean => {
    return false;
  };

  const handleOnUserFormSaved = (response: AxiosResponse<User>) => {
    if (!userToUpdate) {
      dispatch(
        alertActions.addAlert("success", "Éxito", "Usuario actualizado")
      );
    }

    closeUserModal();
    fetchUsers();
  };

  return (
    <>
      <ConditionalRender
        when={isFetching && !(users || fetchError)}
        then={<AppPlaceholder />}
      >
        <SimplePageSection title="Usuarios" />
        <PageSection>
          <AppTableWithControls
            count={filteredItems.length}
            items={pageItems}
            hiddeBottomPagination={true}
            itemsToRow={itemsToRow}
            pagination={paginationQuery}
            sortBy={sortBy}
            handleFilterTextChange={handleFilterTextChange}
            handlePaginationChange={handlePaginationChange}
            handleSortChange={handleSortChange}
            columns={columns}
            actionResolver={actionResolver}
            areActionsDisabled={areActionsDisabled}
            isLoading={isFetching}
            loadingVariant="skeleton"
            fetchError={fetchError}
            filtersApplied={filterText.trim().length > 0}
            toolbar={
              <ToolbarGroup variant="button-group">
                <ToolbarItem>
                  <Button
                    type="button"
                    aria-label="new-user"
                    variant={ButtonVariant.primary}
                    onClick={openCreateUserModal}
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
