import { useMemo } from "react";
import { ISortBy, SortByDirection } from "@patternfly/react-table";

import { PageQuery } from "api/models";

// Hook

interface HookArgs<T> {
  items: T[];
  filterText: string;
  paginationQuery: PageQuery;
  sortBy?: ISortBy;
  compareByColumnIndex: (a: any, b: any, columnIndex?: number) => number;
  filterByText: (filterText: string, value: T) => boolean;
}

interface HookState<T> {
  pageItems: T[];
  filteredItems: T[];
}

export const useTableControlsOffline = <T>({
  items,
  filterText,
  paginationQuery,
  sortBy,
  compareByColumnIndex,
  filterByText,
}: HookArgs<T>): HookState<T> => {
  const state = useMemo(() => {
    //  Sort
    let sortedItems: any[];
    const columnSortIndex = sortBy?.index;
    const columnSortDirection = sortBy?.direction;

    sortedItems = [...items].sort((a, b) =>
      compareByColumnIndex(a, b, columnSortIndex)
    );
    if (columnSortDirection === SortByDirection.desc) {
      sortedItems = sortedItems.reverse();
    }

    // Filter
    const filteredItems = sortedItems.filter((p) =>
      filterByText(filterText, p)
    );

    // Paginate
    const pageItems = filteredItems.slice(
      (paginationQuery.page - 1) * paginationQuery.perPage,
      paginationQuery.page * paginationQuery.perPage
    );

    return {
      filteredItems: filteredItems,
      pageItems: pageItems,
    };
  }, [
    items,
    filterText,
    paginationQuery,
    sortBy,
    compareByColumnIndex,
    filterByText,
  ]);

  return state;
};
