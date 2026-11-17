import { describe, expect, it } from "vitest";
import { render } from "vitest-browser-react";

import checkStyles from "@patternfly/react-styles/css/components/Check/check";
import tableStyles from "@patternfly/react-styles/css/components/Table/table";
import { Table } from "@patternfly/react-table";

import { dataViewBodyStates } from "@app/components/dataViewStates";

const renderEmpty = (args: Parameters<typeof dataViewBodyStates>[0]) => {
  const { bodyStates } = dataViewBodyStates(args);
  return render(<Table aria-label="Results">{bodyStates.empty}</Table>);
};

describe("dataViewBodyStates empty state", () => {
  const base = {
    columnCount: 3,
    isEmpty: true,
    emptyState: <span>No results found</span>,
  };

  it("keeps the selection column narrow without rendering a checkbox", async () => {
    const screen = await renderEmpty({ ...base, hasSelectionColumn: true });
    const selectionCell = screen.getByRole("gridcell").first();

    await expect.element(selectionCell).toHaveClass(tableStyles.tableCheck);
    await expect
      .element(selectionCell)
      .not.toHaveClass(tableStyles.tableToggle);
    // The empty standalone check carries the checkbox's footprint.
    await expect
      .element(selectionCell.element().firstElementChild as HTMLElement)
      .toHaveClass(checkStyles.check, checkStyles.modifiers.standalone);
    await expect.element(screen.getByRole("checkbox")).not.toBeInTheDocument();
  });

  it("renders no control cells when the table has neither", async () => {
    const screen = await renderEmpty(base);

    expect(screen.getByRole("gridcell").elements()).toHaveLength(1);
    await expect
      .element(screen.getByRole("gridcell"))
      .not.toHaveClass(tableStyles.tableCheck, tableStyles.tableToggle);
  });
});
