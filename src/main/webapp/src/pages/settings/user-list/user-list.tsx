import React, { useEffect, useState } from "react";
import { RouteComponentProps } from "react-router-dom";

import { PageSection } from "@patternfly/react-core";
import {
  cellWidth,
  IActions,
  ICell,
  IExtraColumnData,
  IExtraData,
  IRowData,
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
  useFetchUsers,
  useTableControls,
  useTableControlsOffline,
} from "shared/hooks";

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
  const [, setCurrentRow] = useState<User>();

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

  const actions: IActions = [
    {
      title: "Editar",
      onClick: (
        event: React.MouseEvent,
        rowIndex: number,
        rowData: IRowData,
        extraData: IExtraData
      ) => {
        const row: User = getRow(rowData);
        setCurrentRow(row);
      },
    },
    {
      title: "Eliminar",
      onClick: (
        event: React.MouseEvent,
        rowIndex: number,
        rowData: IRowData,
        extraData: IExtraData
      ) => {
        const row: User = getRow(rowData);
        setCurrentRow(row);
      },
    },
  ];

  // const handleOnEditModalClose = () => {
  //   setCurrentRow(undefined);
  // };

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
            actions={actions}
            isLoading={isFetching}
            loadingVariant="skeleton"
            fetchError={fetchError}
            filtersApplied={filterText.trim().length > 0}
          />
        </PageSection>
      </ConditionalRender>
      {/* <UserModal value={currentRow} onClose={handleOnEditModalClose} /> */}
    </>
  );
};
