import React, { useMemo } from "react";

import {
  Toolbar,
  ToolbarContent,
  ToolbarGroup,
  ToolbarItem,
  ToolbarItemVariant,
} from "@patternfly/react-core";
import {
  IActions,
  IActionsResolver,
  IAreActionsDisabled,
  ICell,
  IExtraColumnData,
  IRow,
  ISortBy,
  SortByDirection,
} from "@patternfly/react-table";

import { AppTable } from "../app-table/app-table";
import { SimplePagination } from "../simple-pagination";

import { SearchInput } from "./search-input";

export interface AppTableWithControlsProps {
  count: number;
  items: any[];
  itemsToRow: (items: any[]) => IRow[];

  hiddeBottomPagination?: boolean;
  pagination: {
    perPage?: number;
    page?: number;
  };
  sortBy?: ISortBy;
  handleFilterTextChange: (filterText: string) => void;
  handlePaginationChange: ({
    page,
    perPage,
  }: {
    page: number;
    perPage: number;
  }) => void;
  handleSortChange: (
    event: React.MouseEvent,
    index: number,
    direction: SortByDirection,
    extraData: IExtraColumnData
  ) => void;

  columns: ICell[];
  actions?: IActions;
  actionResolver?: IActionsResolver;
  areActionsDisabled?: IAreActionsDisabled;

  isLoading: boolean;
  loadingVariant?: "skeleton" | "spinner" | "none";
  fetchError?: any;

  toolbar?: any;

  filtersApplied: boolean;
  noDataState?: any;
  noSearchResultsState?: any;
  errorState?: any;
}

export const AppTableWithControls: React.FC<AppTableWithControlsProps> = ({
  count,
  items,
  itemsToRow,

  hiddeBottomPagination,
  pagination,
  sortBy,
  handleFilterTextChange,
  handlePaginationChange,
  handleSortChange,

  columns,
  actions,
  actionResolver,
  areActionsDisabled,

  isLoading,
  fetchError,
  loadingVariant,

  toolbar,

  filtersApplied,
  noDataState,
  noSearchResultsState,
  errorState,
}) => {
  const rows = useMemo(() => itemsToRow(items), [items, itemsToRow]);

  return (
    <div style={{ backgroundColor: "var(--pf-global--BackgroundColor--100)" }}>
      <Toolbar>
        <ToolbarContent>
          <ToolbarGroup>
            <ToolbarItem>
              <SearchInput onSearch={handleFilterTextChange} />
            </ToolbarItem>
          </ToolbarGroup>
          {toolbar}
          <ToolbarItem
            variant={ToolbarItemVariant.pagination}
            alignment={{ default: "alignRight" }}
          >
            <SimplePagination
              count={count}
              params={pagination}
              onChange={handlePaginationChange}
              isTop={true}
            />
          </ToolbarItem>
        </ToolbarContent>
      </Toolbar>
      <AppTable
        columns={columns}
        rows={rows}
        actions={actions}
        actionResolver={actionResolver}
        areActionsDisabled={areActionsDisabled}
        isLoading={isLoading}
        fetchError={fetchError}
        loadingVariant={loadingVariant}
        sortBy={sortBy}
        onSort={handleSortChange}
        filtersApplied={filtersApplied}
        noDataState={noDataState}
        noSearchResultsState={noSearchResultsState}
        errorState={errorState}
      />
      {!hiddeBottomPagination && (
        <SimplePagination
          count={count}
          params={pagination}
          onChange={handlePaginationChange}
        />
      )}
    </div>
  );
};
