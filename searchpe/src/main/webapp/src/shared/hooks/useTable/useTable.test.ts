import { renderHook } from "@testing-library/react";
import { Page } from "shared/types";
import { useTable } from "./useTable";

const range = (n: number) => [...Array(n)].map((_, index) => index + 1);

describe("useTable", () => {
  it("Pagination", () => {
    const items = range(15);
    const page: Page = { page: 1, perPage: 10 };

    const { result } = renderHook(() =>
      useTable<number>({
        items,
        currentPage: page,
        filterItem: () => true,
        compareToByColumn: () => 1,
      })
    );

    expect(result.current.pageItems).toEqual(items.slice(0, 10));
  });

  it("Filter", () => {
    const items = range(15);
    const page: Page = { page: 1, perPage: 10 };

    const { result } = renderHook(() =>
      useTable<number>({
        items,
        currentPage: page,
        filterItem: (value) => value % 2 === 1,
        compareToByColumn: () => 1,
      })
    );

    expect(result.current.pageItems).toEqual([1, 3, 5, 7, 9, 11, 13, 15]);
  });

  it("SortBy", () => {
    const items = range(15);
    const page: Page = { page: 1, perPage: 10 };
    const filterItem = () => true;
    const compareToByColumn = (a: number, b: number, indexCol?: number) =>
      indexCol === 7 ? a - b : 0;

    const { result: resultAsc } = renderHook(() =>
      useTable<number>({
        items,
        currentPage: page,
        filterItem,
        currentSortBy: { direction: "asc", index: 7 },
        compareToByColumn,
      })
    );
    expect(resultAsc.current.pageItems).toEqual(range(10));

    const { result: resultDesc } = renderHook(() =>
      useTable<number>({
        items,
        currentPage: page,
        filterItem,
        currentSortBy: { direction: "desc", index: 7 },
        compareToByColumn,
      })
    );
    expect(resultDesc.current.pageItems).toEqual([
      15, 14, 13, 12, 11, 10, 9, 8, 7, 6,
    ]);
  });

  it("SortBy when 'compareToByColumn' return always 0", () => {
    const items = range(15);
    const page: Page = { page: 1, perPage: 10 };

    const { result: resultAsc } = renderHook(() =>
      useTable<number>({
        items,
        currentPage: page,
        filterItem: () => true,
        currentSortBy: { direction: "asc", index: 7 },
        compareToByColumn: () => 0,
      })
    );
    expect(resultAsc.current.pageItems).toEqual(range(10));

    const { result: resultDesc } = renderHook(() =>
      useTable<number>({
        items,
        currentPage: page,
        filterItem: () => true,
        currentSortBy: { direction: "desc", index: 7 },
        compareToByColumn: () => 0,
      })
    );
    expect(resultDesc.current.pageItems).toEqual(range(10));
  });

  it("SortBy when 'compareToByColumn' return always 0 and 'filter' is applied", () => {
    const items = range(25);
    const page: Page = { page: 1, perPage: 10 };
    const filterItem = (val: number) => val % 2 === 0;
    const expectedResult = [2, 4, 6, 8, 10, 12, 14, 16, 18, 20];

    const { result: resultAsc } = renderHook(() =>
      useTable<number>({
        items,
        currentPage: page,
        filterItem,
        currentSortBy: { direction: "asc", index: 7 },
        compareToByColumn: () => 0,
      })
    );
    expect(resultAsc.current.pageItems).toEqual(expectedResult);

    const { result: resultDesc } = renderHook(() =>
      useTable<number>({
        items,
        currentPage: page,
        filterItem,
        currentSortBy: { direction: "desc", index: 7 },
        compareToByColumn: () => 0,
      })
    );
    expect(resultDesc.current.pageItems).toEqual(expectedResult);
  });
});
