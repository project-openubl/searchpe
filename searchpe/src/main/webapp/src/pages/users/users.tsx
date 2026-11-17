import React, { useState } from "react";

import {
  Button,
  ButtonVariant,
  Modal,
  ModalBody,
  ModalHeader,
  ModalVariant,
  PageSection,
  ToolbarGroup,
  ToolbarItem,
} from "@patternfly/react-core";
import { IAction } from "@patternfly/react-table";

import { User } from "api/models";

import {
  ConditionalRender,
  SearchInput,
  SimplePageSection,
  SimplePlaceholder,
  SimpleTableColumn,
  SimpleTableWithToolbar,
} from "shared/components";
import { useConfirmationContext, useNotificationContext } from "shared/context";
import { useModal, useTable, useTableControls } from "shared/hooks";

import { UserForm } from "./components/user-form";
import { getAxiosErrorMessage } from "utils/modelUtils";
import { useDeleteUserMutation, useUsersQuery } from "queries/users";

const columns: SimpleTableColumn[] = [
  { title: "Usuario", width: 30, isSortable: true },
  { title: "Permisos", width: 50 },
  { title: "Nombre", width: 20 },
];

const renderCells = (item: User): React.ReactNode[] => [
  item.username,
  item.permissions?.join(", "),
  item.fullName,
];

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
  const { pushNotification } = useNotificationContext();
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

  const actionResolver = (row: User): IAction[] => {
    return [
      {
        title: "Editar",
        onClick: () => openUserModal("update", row),
      },
      {
        title: "Eliminar",
        onClick: () => {
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
                  pushNotification({
                    variant: "danger",
                    title: "Error",
                    message: getAxiosErrorMessage(error),
                  });
                })
                .finally(() => {
                  confirmation.close();
                });
            },
          });
        },
      },
    ];
  };

  const handleOnUserFormSaved = () => {
    if (!userToUpdate) {
      pushNotification({
        variant: "success",
        title: "Éxito",
        message: "Usuario actualizado",
      });
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
            itemKey={(item) => item.id ?? item.username}
            renderCells={renderCells}
            actionResolver={actionResolver}
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
              <ToolbarGroup variant="action-group">
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
        variant={ModalVariant.medium}
        isOpen={isUserModalOpen}
        onClose={closeUserModal}
        aria-labelledby="user-modal-title"
      >
        <ModalHeader
          title={`${userToUpdate ? "Editar" : "Crear"} usuario`}
          labelId="user-modal-title"
        />
        <ModalBody>
          <UserForm
            user={userToUpdate}
            onSaved={handleOnUserFormSaved}
            onCancel={closeUserModal}
          />
        </ModalBody>
      </Modal>
    </>
  );
};
