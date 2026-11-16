import React from "react";
import { Pagination, PaginationVariant } from "@patternfly/react-core";

// Inlined (and ported to PatternFly 6) from @project-openubl/lib-ui

export interface ISimplePaginationProps {
  count: number;
  params: {
    perPage?: number;
    page?: number;
  };

  isTop?: boolean;
  isCompact?: boolean;
  perPageOptions?: number[];
  onChange: ({ page, perPage }: { page: number; perPage: number }) => void;
}

export const SimplePagination: React.FC<ISimplePaginationProps> = ({
  count,
  params,
  isTop,
  isCompact,
  perPageOptions,
  onChange,
}) => {
  const perPage = params.perPage || 10;

  return (
    <Pagination
      itemCount={count}
      page={params.page || 1}
      perPage={perPage}
      onPageInput={(_, page) => onChange({ page, perPage })}
      onSetPage={(_, page) => onChange({ page, perPage })}
      onPerPageSelect={(_, perPage) => onChange({ page: 1, perPage })}
      isCompact={isTop || isCompact}
      widgetId={isTop ? "pagination-top" : "pagination-bottom"}
      variant={isTop ? PaginationVariant.top : PaginationVariant.bottom}
      perPageOptions={(perPageOptions || [10, 20, 50, 100]).map((option) => ({
        title: String(option),
        value: option,
      }))}
    />
  );
};
