import React from "react";
import {
  Toolbar,
  ToolbarContent,
  ToolbarItem,
  ToolbarItemVariant,
  ToolbarToggleGroup,
} from "@patternfly/react-core";
import { FilterIcon } from "@patternfly/react-icons";
import { t_global_background_color_primary_default as backgroundColor } from "@patternfly/react-tokens";

import { SimpleTable, ISimpleTableProps } from "./simple-table";
import { SimplePagination } from "./simple-pagination";

// Re-implementation (PatternFly 6) of the SimpleTableWithToolbar component
// that used to live in @project-openubl/lib-ui

export interface ISimpleTableWithToolbarProps<T> extends ISimpleTableProps<T> {
  totalCount: number;

  currentPage: {
    page?: number;
    perPage?: number;
  };
  onPageChange: ({ page, perPage }: { page: number; perPage: number }) => void;

  hasTopPagination?: boolean;
  hasBottomPagination?: boolean;

  toolbarToggle?: React.ReactNode;
  toolbarActions?: React.ReactNode;
  toolbarClearAllFilters?: () => void;
}

export const SimpleTableWithToolbar = <T,>({
  totalCount,
  currentPage,
  onPageChange,

  hasTopPagination = false,
  hasBottomPagination = false,

  toolbarToggle,
  toolbarActions,
  toolbarClearAllFilters,

  ...rest
}: ISimpleTableWithToolbarProps<T>) => {
  return (
    <div style={{ backgroundColor: backgroundColor.var }}>
      <Toolbar
        collapseListedFiltersBreakpoint="xl"
        clearAllFilters={toolbarClearAllFilters}
      >
        <ToolbarContent>
          {toolbarToggle && (
            <ToolbarToggleGroup toggleIcon={<FilterIcon />} breakpoint="xl">
              {toolbarToggle}
            </ToolbarToggleGroup>
          )}
          {toolbarActions}
          {hasTopPagination && (
            <ToolbarItem
              variant={ToolbarItemVariant.pagination}
              align={{ default: "alignEnd" }}
            >
              <SimplePagination
                count={totalCount}
                params={currentPage}
                onChange={onPageChange}
                isTop
              />
            </ToolbarItem>
          )}
        </ToolbarContent>
      </Toolbar>
      <SimpleTable {...rest} />
      {hasBottomPagination && (
        <SimplePagination
          count={totalCount}
          params={currentPage}
          onChange={onPageChange}
        />
      )}
    </div>
  );
};
