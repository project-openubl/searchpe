import { describe, expect, it, vi } from "vitest";
import { render } from "vitest-browser-react";

import { OseLookupForm } from "./OseLookupForm";

const renderForm = () => {
  const onSubmit = vi.fn();
  return {
    onSubmit,
    screen: render(
      <OseLookupForm
        formId="ose"
        submitError={null}
        isSubmitting={false}
        onSubmit={onSubmit}
      />,
    ),
  };
};

describe("OseLookupForm", () => {
  it("asks for every field in Spanish", async () => {
    const { onSubmit, screen: rendering } = renderForm();
    const screen = await rendering;

    await screen.getByRole("button", { name: "Consultar" }).click();

    await expect
      .element(screen.getByText("Ingresa el RUC del emisor"))
      .toBeVisible();
    await expect.element(screen.getByText("Ingresa la serie")).toBeVisible();
    await expect.element(screen.getByText("Ingresa el número")).toBeVisible();
    await expect
      .element(screen.getByText("Completa la verificación"))
      .toBeVisible();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("sends the printed data with the serie in capitals and the número as a number", async () => {
    const { onSubmit, screen: rendering } = renderForm();
    const screen = await rendering;

    await screen
      .getByRole("textbox", { name: "RUC del emisor" })
      .fill("20123456789");
    await screen.getByRole("textbox", { name: "Serie" }).fill("f001");
    await screen.getByRole("textbox", { name: "Número" }).fill("123");
    await screen
      .getByRole("textbox", { name: "Código de verificación" })
      .fill("test-pass");
    await screen.getByRole("button", { name: "Consultar" }).click();

    await expect.poll(() => onSubmit.mock.calls.length).toBe(1);
    expect(onSubmit).toHaveBeenCalledWith({
      ruc: "20123456789",
      tipo_comprobante: "01",
      serie: "F001",
      numero: 123,
      captcha: "test-pass",
    });
  });

  it("rejects a RUC that isn't 11 digits", async () => {
    const { screen: rendering } = renderForm();
    const screen = await rendering;

    await screen.getByRole("textbox", { name: "RUC del emisor" }).fill("123");
    await screen.getByRole("button", { name: "Consultar" }).click();

    await expect
      .element(screen.getByText("El RUC tiene 11 dígitos"))
      .toBeVisible();
  });
});
