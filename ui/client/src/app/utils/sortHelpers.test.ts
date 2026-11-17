import type React from "react";

import { describe, expect, it, vi } from "vitest";

import { buildThSort, toSortByParam } from "@app/utils/sortHelpers";

describe("buildThSort", () => {
  const columnKeys = ["nombre", "ruc", "created_at"] as const;

  it("marks the active column with its index and direction", () => {
    const sort = buildThSort({
      columnKeys,
      columnKey: "created_at",
      sortBy: "created_at",
      direction: "desc",
      onSort: vi.fn(),
    });

    expect(sort?.columnIndex).toBe(2);
    expect(sort?.sortBy).toEqual({ index: 2, direction: "desc" });
  });

  it("leaves index/direction unset for non-active columns", () => {
    const sort = buildThSort({
      columnKeys,
      columnKey: "nombre",
      sortBy: undefined,
      direction: undefined,
      onSort: vi.fn(),
    });

    expect(sort?.sortBy).toEqual({ index: undefined, direction: undefined });
  });

  it("bridges PatternFly's index-based onSort back to the column key", () => {
    const onSort = vi.fn();
    const sort = buildThSort({
      columnKeys,
      columnKey: "nombre",
      sortBy: "nombre",
      direction: "asc",
      onSort,
    });

    sort?.onSort?.({} as React.MouseEvent, 0, "desc" as never, {} as never);

    expect(onSort).toHaveBeenCalledWith(expect.anything(), "nombre", "desc");
  });
});

describe("toSortByParam", () => {
  it("builds searchpe's field:direction value", () => {
    expect(toSortByParam({ sortBy: "nombre", direction: "desc" })).toBe(
      "nombre:desc",
    );
    expect(toSortByParam({ sortBy: "nombre", direction: "asc" })).toBe(
      "nombre:asc",
    );
    expect(toSortByParam({ sortBy: undefined, direction: "asc" })).toBe(
      undefined,
    );
  });
});
