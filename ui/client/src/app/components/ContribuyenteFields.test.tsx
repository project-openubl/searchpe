import { describe, expect, it } from "vitest";
import { render } from "vitest-browser-react";

import { ContribuyenteFields } from "./ContribuyenteFields";

describe("ContribuyenteFields", () => {
  it("shows the padrón data, with a dash for empty columns", async () => {
    const screen = await render(
      <ContribuyenteFields
        contribuyente={{
          ruc: "20123456789",
          nombre: "BODEGA SAN JUAN S.A.C.",
          estado: "ACTIVO",
          condicionDomicilio: "HABIDO",
          interior: "-",
        }}
      />,
    );

    await expect.element(screen.getByText("20123456789")).toBeVisible();
    await expect
      .element(screen.getByText("BODEGA SAN JUAN S.A.C."))
      .toBeVisible();
    await expect.element(screen.getByText("ACTIVO")).toBeVisible();
    await expect.element(screen.getByText("HABIDO")).toBeVisible();
    // DNI, interior and the other empty columns.
    expect(
      screen.getByText("-", { exact: true }).elements().length,
    ).toBeGreaterThan(5);
  });
});
