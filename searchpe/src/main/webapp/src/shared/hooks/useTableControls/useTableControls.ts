import { useCallback, useState } from "react";
import { Page, SortBy } from "shared/types";

// Inlined from @project-openubl/lib-ui

const DEFAULT_PAGE: Page = { page: 1, perPage: 10 };

interface HookArgs {
  page?: Page;
  sortBy?: SortBy;
}

interface HookState {
  page: Page;
  sortBy?: SortBy;
  changePage: (page: { page: number; perPage?: number }) => void;
  /** Signature compatible with PatternFly's `<Th sort={{ onSort }} />` */
  changeSortBy: (
    event: React.MouseEvent | undefined,
    index: number,
    direction: SortBy["direction"]
  ) => void;
}

export const useTableControls = (args?: HookArgs): HookState => {
  const [page, setPage] = useState<Page>({ ...(args?.page ?? DEFAULT_PAGE) });
  const [sortBy, setSortBy] = useState<SortBy | undefined>(
    args?.sortBy ? { ...args.sortBy } : undefined
  );

  const changePage = useCallback(
    (newPage: { page: number; perPage?: number }) => {
      setPage((current) => ({
        page: newPage.page >= 1 ? newPage.page : 1,
        perPage: newPage.perPage ?? current.perPage,
      }));
    },
    []
  );

  const changeSortBy = useCallback(
    (
      _event: React.MouseEvent | undefined,
      index: number,
      direction: SortBy["direction"]
    ) => {
      setSortBy({ index, direction });
    },
    []
  );

  return { page, sortBy, changePage, changeSortBy };
};
