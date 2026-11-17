import type React from "react";

import type {
  PaginationTitles,
  PaginationVariant,
  PerPageOptions,
} from "@patternfly/react-core";
import { Pagination } from "@patternfly/react-core";

import {
  isPerPage,
  type ListPaginationState,
  PER_PAGE_VALUES,
} from "@app/utils/listState";

/** Per-page choices for every list built on `ListPagination` (10 by default, never above 20). */
const PER_PAGE_OPTIONS: PerPageOptions[] = PER_PAGE_VALUES.map((value) => ({
  title: String(value),
  value,
}));

/** PatternFly's pager in Spanish (ADR 0005). */
const TITLES: PaginationTitles = {
  items: "",
  page: "",
  pages: "páginas",
  itemsPerPage: "Elementos por página",
  perPageSuffix: "por página",
  toFirstPageAriaLabel: "Ir a la primera página",
  toPreviousPageAriaLabel: "Ir a la página anterior",
  toNextPageAriaLabel: "Ir a la página siguiente",
  toLastPageAriaLabel: "Ir a la última página",
  currPageAriaLabel: "Página actual",
  paginationAriaLabel: "Paginación",
  optionsToggleAriaLabel: "Elementos por página",
  ofWord: "de",
};

interface IListPaginationProps {
  variant: PaginationVariant;
  itemCount: number;
  /** The API gave no total (`estimateItemCount`): show "1 - 10 de muchos" while more may follow. */
  isCountEstimated?: boolean;
  pagination: ListPaginationState;
  onPaginationChange: (next: ListPaginationState) => void;
}

/** Standard list `<Pagination>` wiring, so list pages don't each repeat it. */
export const ListPagination: React.FC<IListPaginationProps> = ({
  variant,
  itemCount,
  isCountEstimated,
  pagination,
  onPaginationChange,
}) => (
  <Pagination
    variant={variant}
    itemCount={itemCount}
    page={pagination.page}
    perPage={pagination.perPage}
    onSetPage={(_event, page) => onPaginationChange({ ...pagination, page })}
    onPerPageSelect={(_event, perPage) =>
      // Only PER_PAGE_OPTIONS are offered, so the guard never drops a choice.
      isPerPage(perPage) && onPaginationChange({ page: 1, perPage })
    }
    perPageOptions={PER_PAGE_OPTIONS}
    titles={TITLES}
    toggleTemplate={({ firstIndex, lastIndex, itemCount: total }) => (
      <>
        <b>
          {firstIndex} - {lastIndex}
        </b>{" "}
        de{" "}
        <b>
          {isCountEstimated && (lastIndex ?? 0) < (total ?? 0)
            ? "muchos"
            : total}
        </b>
      </>
    )}
  />
);
