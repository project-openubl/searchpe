import { describe, expect, it } from "vitest";
import { render } from "vitest-browser-react";

import { FormSubmitError } from "./FormSubmitError";

describe("FormSubmitError", () => {
  it("renders nothing when there is no error", async () => {
    const screen = await render(<FormSubmitError error={null} />);
    await expect.element(screen.container).toBeEmptyDOMElement();
  });

  it("renders a danger alert with the error title", async () => {
    const screen = await render(
      <FormSubmitError error={{ title: "No se pudo crear la versión" }} />,
    );
    await expect
      .element(
        screen.getByRole("heading", {
          name: /^Danger alert:\s*No se pudo crear la versión$/,
        }),
      )
      .toBeVisible();
  });

  it("includes the description when present", async () => {
    const screen = await render(
      <FormSubmitError
        error={{
          title: "No se pudo crear la versión",
          description: "El padrón aún se está importando.",
        }}
      />,
    );
    await expect
      .element(screen.getByText("El padrón aún se está importando."))
      .toBeVisible();
  });
});
