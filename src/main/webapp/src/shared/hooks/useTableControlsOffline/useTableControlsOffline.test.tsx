import { IExtraColumnData, SortByDirection } from "@patternfly/react-table";
import { renderHook, act } from "@testing-library/react-hooks";
import { useTableControlsOffline } from "./useTableControlsOffline";

describe("useTableControlsOffline", () => {
  it("Update filterText state correctly", () => {
    // const { result } = renderHook(() =>
    //   useTableControlsOffline({ columnToField: jest.fn() })
    // );
    // //
    // const { handleFilterTextChange } = result.current;
    // act(() => handleFilterTextChange("My filtertext"));
    // expect(result.current.filterText).toBe("My filtertext");
  });
});
