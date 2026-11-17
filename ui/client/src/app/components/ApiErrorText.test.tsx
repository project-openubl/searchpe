import { describe, expect, it } from "vitest";
import { render } from "vitest-browser-react";

import { ApiErrorText } from "./ApiErrorText";

describe("ApiErrorText", () => {
  it("shows the message and then the detail", async () => {
    const screen = await render(
      <ApiErrorText
        error={{
          title: "No se pudo emitir",
          description: "El comprobante no es válido.",
          detail: "SUNAT 2335",
        }}
      />,
    );

    await expect
      .element(screen.getByText("El comprobante no es válido."))
      .toBeVisible();
    await expect.element(screen.getByText("SUNAT 2335")).toBeVisible();
  });
});
