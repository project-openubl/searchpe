import React from "react";
import {
  Bullseye,
  EmptyState,
  EmptyStateBody,
  EmptyStateVariant,
  Skeleton,
  Spinner,
} from "@patternfly/react-core";
import {
  ActionsColumn,
  IAction,
  SortByDirection,
  Table,
  Tbody,
  Td,
  Th,
  ThProps,
  Thead,
  Tr,
} from "@patternfly/react-table";
import {
  CubesIcon,
  ExclamationCircleIcon,
  SearchIcon,
} from "@patternfly/react-icons";

import { SortBy } from "shared/types";

// Re-implementation (PatternFly 6 composable Table) of the
// SimpleTable component that used to live in @project-openubl/lib-ui

export interface SimpleTableColumn {
  title: string;
  width?: ThProps["width"];
  isSortable?: boolean;
}

export interface ISimpleTableProps<T> {
  "aria-label"?: string;

  columns: SimpleTableColumn[];
  items: T[];
  /** Unique key for each item */
  itemKey: (item: T) => React.Key;
  /** One node per column */
  renderCells: (item: T) => React.ReactNode[];

  actionResolver?: (item: T) => IAction[];
  areActionsDisabled?: (item: T) => boolean;

  sortBy?: SortBy;
  onSort?: (
    event: React.MouseEvent | undefined,
    index: number,
    direction: SortBy["direction"]
  ) => void;

  isLoading: boolean;
  loadingVariant?: "skeleton" | "spinner" | "none";
  fetchError?: unknown;

  filtersApplied: boolean;
  noDataState?: React.ReactNode;
  noSearchResultsState?: React.ReactNode;
  errorState?: React.ReactNode;
}

export const SimpleStateNoData: React.FC = () => (
  <EmptyState
    variant={EmptyStateVariant.sm}
    titleText="No data available"
    headingLevel="h2"
    icon={CubesIcon}
  >
    <EmptyStateBody>No data available to be shown here.</EmptyStateBody>
  </EmptyState>
);

export const SimpleStateNoResults: React.FC = () => (
  <EmptyState
    variant={EmptyStateVariant.sm}
    titleText="No results found"
    headingLevel="h2"
    icon={SearchIcon}
  >
    <EmptyStateBody>
      No results match the filter criteria. Remove all filters or clear all
      filters to show results.
    </EmptyStateBody>
  </EmptyState>
);

export const SimpleStateError: React.FC = () => (
  <EmptyState
    variant={EmptyStateVariant.sm}
    titleText="Unable to connect"
    headingLevel="h2"
    icon={ExclamationCircleIcon}
    status="danger"
  >
    <EmptyStateBody>
      There was an error retrieving data. Check your connection and try again.
    </EmptyStateBody>
  </EmptyState>
);

export const SimpleTable = <T,>({
  "aria-label": ariaLabel = "main-table",
  columns,
  items,
  itemKey,
  renderCells,
  actionResolver,
  areActionsDisabled,
  sortBy,
  onSort,
  isLoading,
  loadingVariant = "skeleton",
  fetchError,
  filtersApplied,
  noDataState,
  noSearchResultsState,
  errorState,
}: ISimpleTableProps<T>) => {
  const hasActions = !!actionResolver;
  const columnCount = columns.length + (hasActions ? 1 : 0);

  const fullWidthRow = (content: React.ReactNode) => (
    <Tr>
      <Td colSpan={columnCount}>
        <Bullseye>{content}</Bullseye>
      </Td>
    </Tr>
  );

  let body: React.ReactNode;
  if (isLoading && loadingVariant !== "none") {
    body =
      loadingVariant === "skeleton"
        ? [...Array(10)].map((_, rowIndex) => (
            <Tr key={rowIndex}>
              {[...Array(columnCount)].map((_, cellIndex) => (
                <Td key={cellIndex}>
                  <Skeleton />
                </Td>
              ))}
            </Tr>
          ))
        : fullWidthRow(<Spinner size="xl" />);
  } else if (fetchError) {
    body = fullWidthRow(errorState ?? <SimpleStateError />);
  } else if (items.length === 0) {
    body = fullWidthRow(
      filtersApplied
        ? (noSearchResultsState ?? <SimpleStateNoResults />)
        : (noDataState ?? <SimpleStateNoData />)
    );
  } else {
    body = items.map((item) => {
      const cells = renderCells(item);
      return (
        <Tr key={itemKey(item)}>
          {columns.map((column, columnIndex) => (
            <Td key={columnIndex} dataLabel={column.title}>
              {cells[columnIndex]}
            </Td>
          ))}
          {actionResolver && (
            <Td isActionCell>
              <ActionsColumn
                items={actionResolver(item)}
                isDisabled={areActionsDisabled?.(item)}
              />
            </Td>
          )}
        </Tr>
      );
    });
  }

  return (
    <Table aria-label={ariaLabel}>
      <Thead>
        <Tr>
          {columns.map((column, columnIndex) => (
            <Th
              key={columnIndex}
              width={column.width}
              sort={
                column.isSortable && onSort
                  ? {
                      columnIndex,
                      sortBy: {
                        index: sortBy?.index,
                        direction: sortBy?.direction,
                        defaultDirection: "asc",
                      },
                      onSort: (event, index, direction) =>
                        onSort(
                          event,
                          index,
                          direction === SortByDirection.desc ? "desc" : "asc"
                        ),
                    }
                  : undefined
              }
            >
              {column.title}
            </Th>
          ))}
          {hasActions && <Th screenReaderText="Actions" />}
        </Tr>
      </Thead>
      <Tbody>{body}</Tbody>
    </Table>
  );
};
