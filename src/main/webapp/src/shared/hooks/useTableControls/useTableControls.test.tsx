import { IExtraColumnData, SortByDirection } from "@patternfly/react-table";
import { renderHook, act } from "@testing-library/react-hooks";
import { useTableControls } from "./useTableControls";

describe("useTableControls", () => {
  it("Update filterText state correctly", () => {
    const { result } = renderHook(() =>
      useTableControls({ columnToField: jest.fn() })
    );

    //
    const { handleFilterTextChange } = result.current;
    act(() => handleFilterTextChange("My filtertext"));
    expect(result.current.filterText).toBe("My filtertext");
  });

  it("Update pagination correctly", () => {
    const { result } = renderHook(() =>
      useTableControls({ columnToField: jest.fn() })
    );

    //
    const { handlePaginationChange } = result.current;
    act(() => handlePaginationChange({ page: 2, perPage: 50 }));
    expect(result.current.paginationQuery).toMatchObject({
      page: 2,
      perPage: 50,
    });
  });

  it("Update state sortBy correctly", () => {
    const COLUMN_INDEX = 2;
    const FIELD_NAME = "myField";

    const columnToField = (
      _: React.MouseEvent,
      index: number,
      direction: SortByDirection,
      extraData: IExtraColumnData
    ) => {
      if (index === COLUMN_INDEX) {
        return FIELD_NAME;
      }

      throw new Error("Invalid index");
    };

    const { result } = renderHook(() => useTableControls({ columnToField }));

    //
    const { handleSortChange } = result.current;
    act(() =>
      handleSortChange(
        jest.fn() as any,
        COLUMN_INDEX,
        SortByDirection.desc,
        jest.fn() as any
      )
    );

    expect(result.current.sortBy).toMatchObject({
      index: COLUMN_INDEX,
      direction: SortByDirection.desc,
    });
    expect(result.current.sortByQuery).toMatchObject({
      orderBy: FIELD_NAME,
      orderDirection: SortByDirection.desc,
    });
  });
});
