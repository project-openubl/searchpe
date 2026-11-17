import { describe, expect, it } from "vitest";
import { render } from "vitest-browser-react";

import { Button } from "@patternfly/react-core";

import { FullPageEmptyState } from "./FullPageEmptyState";

const Icon = () => <svg />;

describe("FullPageEmptyState", () => {
  it("renders the title as a level-1 heading and the body", async () => {
    const screen = await render(
      <FullPageEmptyState
        titleText="Something went wrong"
        icon={Icon}
        body="Try again."
      />,
    );

    await expect
      .element(
        screen.getByRole("heading", { level: 1, name: "Something went wrong" }),
      )
      .toBeVisible();
    await expect.element(screen.getByText("Try again.")).toBeVisible();
  });

  it("renders footer actions when provided", async () => {
    const screen = await render(
      <FullPageEmptyState
        titleText="Error"
        icon={Icon}
        body="Body"
        actions={<Button>Go home</Button>}
      />,
    );

    await expect
      .element(screen.getByRole("button", { name: "Go home" }))
      .toBeVisible();
  });

  it("omits the footer when no actions are given", async () => {
    const screen = await render(
      <FullPageEmptyState titleText="Error" icon={Icon} body="Body" />,
    );

    await expect.element(screen.getByText("Body")).toBeVisible();
    await expect.element(screen.getByRole("button")).not.toBeInTheDocument();
  });
});
