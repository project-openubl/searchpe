import { act, renderHook } from "@testing-library/react";
import { useTableControls } from "./useTableControls";

describe("useTableControls", () => {
  it("Update pagination correctly", () => {
    const { result } = renderHook(() => useTableControls());

    act(() => result.current.changePage({ page: 2, perPage: 50 }));
    expect(result.current.page).toMatchObject({ page: 2, perPage: 50 });
  });

  it("Keeps perPage when not provided", () => {
    const { result } = renderHook(() =>
      useTableControls({ page: { page: 1, perPage: 20 } })
    );

    act(() => result.current.changePage({ page: 3 }));
    expect(result.current.page).toMatchObject({ page: 3, perPage: 20 });
  });

  it("Update state sortBy correctly", () => {
    const COLUMN_INDEX = 2;
    const { result } = renderHook(() => useTableControls());

    act(() => result.current.changeSortBy(undefined, COLUMN_INDEX, "desc"));
    expect(result.current.sortBy).toMatchObject({
      index: COLUMN_INDEX,
      direction: "desc",
    });
  });

  it("Start with default pagination", () => {
    const { result } = renderHook(() =>
      useTableControls({ page: { page: 2, perPage: 50 } })
    );

    expect(result.current.page.page).toBe(2);
    expect(result.current.page.perPage).toBe(50);
  });

  it("Start with default sortBy", () => {
    const { result } = renderHook(() =>
      useTableControls({
        page: { page: 3, perPage: 100 },
        sortBy: { index: 2, direction: "desc" },
      })
    );

    expect(result.current.sortBy?.index).toBe(2);
    expect(result.current.sortBy?.direction).toBe("desc");
  });

  it("Doesn't allow Zero or negative page values", () => {
    const { result } = renderHook(() => useTableControls());

    act(() => result.current.changePage({ page: 0, perPage: 50 }));
    expect(result.current.page).toMatchObject({ page: 1, perPage: 50 });

    act(() => result.current.changePage({ page: -1, perPage: 50 }));
    expect(result.current.page).toMatchObject({ page: 1, perPage: 50 });
  });
});
