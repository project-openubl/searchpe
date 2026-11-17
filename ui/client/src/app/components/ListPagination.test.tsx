import { describe, expect, it, vi } from "vitest";
import { render } from "vitest-browser-react";

import { PaginationVariant } from "@patternfly/react-core";

import { estimateItemCount } from "@app/utils/listState";

import { ListPagination } from "./ListPagination";

describe("ListPagination", () => {
  it("reads in Spanish with a known total", async () => {
    const screen = await render(
      <ListPagination
        variant={PaginationVariant.top}
        itemCount={42}
        pagination={{ page: 1, perPage: 10 }}
        onPaginationChange={vi.fn()}
      />,
    );

    await expect.element(screen.getByText("de").first()).toBeVisible();
    await expect.element(screen.getByText("42").first()).toBeVisible();
  });

  it("says 'muchos' while an estimated list may go on, and moves to the next page", async () => {
    const onPaginationChange = vi.fn();
    const pagination = { page: 1, perPage: 10 } as const;
    // The bottom pager: the top one hides its arrows on a narrow screen like the test's.
    const screen = await render(
      <ListPagination
        variant={PaginationVariant.bottom}
        itemCount={estimateItemCount({ pagination, pageSize: 10 })}
        isCountEstimated
        pagination={pagination}
        onPaginationChange={onPaginationChange}
      />,
    );

    await expect.element(screen.getByText("muchos").first()).toBeVisible();
    await screen
      .getByRole("button", { name: "Ir a la página siguiente" })
      .click();
    expect(onPaginationChange).toHaveBeenCalledWith({ page: 2, perPage: 10 });
  });
});
