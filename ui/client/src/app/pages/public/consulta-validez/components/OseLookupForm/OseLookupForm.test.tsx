import { type Ref, useImperativeHandle } from "react";

import { beforeEach, describe, expect, it, vi } from "vitest";
import { render } from "vitest-browser-react";

import type { ICaptchaConfig } from "@openubl-ui/common";

import { OseLookupForm } from "./OseLookupForm";

const widget = vi.hoisted(() => ({ reset: vi.fn() }));

interface IFakeWidgetProps {
  ref?: Ref<unknown>;
  sitekey?: string;
  siteKey?: string;
  onVerify?: (token: string, key: string) => void;
  onSuccess?: (token: string) => void;
  onError?: (code: string) => void;
}

// Stand-ins for the providers' wrappers: no script from the internet, a button solves it.
vi.mock(import("@hcaptcha/react-hcaptcha"), () => ({
  default: (({ ref, sitekey, onVerify, onError }: IFakeWidgetProps) => {
    useImperativeHandle(ref, () => ({ resetCaptcha: widget.reset }));
    return (
      <div>
        <span>hCaptcha {sitekey}</span>
        <button type="button" onClick={() => onVerify?.("h-token", "ekey")}>
          Resolver
        </button>
        <button type="button" onClick={() => onError?.("network-error")}>
          Fallar
        </button>
      </div>
    );
  }) as never,
}));

vi.mock(import("@marsidev/react-turnstile"), () => ({
  Turnstile: (({ ref, siteKey, onSuccess }: IFakeWidgetProps) => {
    useImperativeHandle(ref, () => ({ reset: widget.reset }));
    return (
      <div>
        <span>Turnstile {siteKey}</span>
        <button type="button" onClick={() => onSuccess?.("t-token")}>
          Resolver
        </button>
      </div>
    );
  }) as never,
}));

const HCAPTCHA: ICaptchaConfig = { provider: "hcaptcha", siteKey: "site-h" };

const renderForm = (captcha?: ICaptchaConfig) => {
  const onSubmit = vi.fn();
  return {
    onSubmit,
    screen: render(
      <OseLookupForm
        formId="ose"
        submitError={null}
        isSubmitting={false}
        onSubmit={onSubmit}
        captcha={captcha}
      />,
    ),
  };
};

type Screen = Awaited<ReturnType<typeof renderForm>["screen"]>;

const fillDocument = async (screen: Screen) => {
  await screen
    .getByRole("textbox", { name: "RUC del emisor" })
    .fill("20123456789");
  await screen.getByRole("textbox", { name: "Serie" }).fill("F001");
  await screen.getByRole("textbox", { name: "Número" }).fill("7");
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

  it("shows the text field, not a widget, without a site key", async () => {
    const { screen: rendering } = renderForm();
    const screen = await rendering;

    await expect
      .element(screen.getByRole("textbox", { name: "Código de verificación" }))
      .toBeVisible();
    await expect.element(screen.getByText(/hCaptcha/)).not.toBeInTheDocument();
  });
});

describe("OseLookupForm with a CAPTCHA widget", () => {
  beforeEach(() => {
    widget.reset.mockClear();
  });

  it("shows the provider's widget instead of the text field", async () => {
    const { screen: rendering } = renderForm(HCAPTCHA);
    const screen = await rendering;

    await expect
      .element(screen.getByRole("group", { name: "Verificación" }))
      .toBeVisible();
    await expect.element(screen.getByText("hCaptcha site-h")).toBeVisible();
    await expect
      .element(screen.getByRole("textbox", { name: "Código de verificación" }))
      .not.toBeInTheDocument();
  });

  it("asks to solve the widget before sending", async () => {
    const { onSubmit, screen: rendering } = renderForm(HCAPTCHA);
    const screen = await rendering;

    await fillDocument(screen);
    await screen.getByRole("button", { name: "Consultar" }).click();

    await expect
      .element(screen.getByText("Completa la verificación"))
      .toBeVisible();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("sends the widget's token and resets the widget for the next query", async () => {
    const { onSubmit, screen: rendering } = renderForm(HCAPTCHA);
    const screen = await rendering;

    await fillDocument(screen);
    await screen.getByRole("button", { name: "Resolver" }).click();
    await screen.getByRole("button", { name: "Consultar" }).click();

    await expect.poll(() => onSubmit.mock.calls.length).toBe(1);
    expect(onSubmit).toHaveBeenCalledWith({
      ruc: "20123456789",
      tipo_comprobante: "01",
      serie: "F001",
      numero: 7,
      captcha: "h-token",
    });
    expect(widget.reset).toHaveBeenCalledOnce();

    // The token was spent: a second query needs a new verification.
    await screen.getByRole("button", { name: "Consultar" }).click();
    await expect
      .element(screen.getByText("Completa la verificación"))
      .toBeVisible();
    expect(onSubmit).toHaveBeenCalledOnce();
  });

  it("asks for a new verification after a failed query", async () => {
    const onSubmit = vi.fn();
    const screen = await render(
      <OseLookupForm
        formId="ose"
        submitError={null}
        isSubmitting={false}
        onSubmit={onSubmit}
        captcha={HCAPTCHA}
      />,
    );

    await fillDocument(screen);
    await screen.getByRole("button", { name: "Resolver" }).click();
    await screen.getByRole("button", { name: "Consultar" }).click();
    await expect.poll(() => onSubmit.mock.calls.length).toBe(1);

    await screen.rerender(
      <OseLookupForm
        formId="ose"
        submitError={{
          title: "No se pudo consultar el comprobante",
          description: "Verificación CAPTCHA inválida.",
        }}
        isSubmitting={false}
        onSubmit={onSubmit}
        captcha={HCAPTCHA}
      />,
    );

    await expect
      .element(screen.getByText("Verificación CAPTCHA inválida."))
      .toBeVisible();
    expect(widget.reset).toHaveBeenCalledOnce();
    await screen.getByRole("button", { name: "Consultar" }).click();
    await expect
      .element(screen.getByText("Completa la verificación"))
      .toBeVisible();
    expect(onSubmit).toHaveBeenCalledOnce();
  });

  it("drops the token and says so when the widget fails", async () => {
    const { onSubmit, screen: rendering } = renderForm(HCAPTCHA);
    const screen = await rendering;

    await fillDocument(screen);
    await screen.getByRole("button", { name: "Resolver" }).click();
    await screen.getByRole("button", { name: "Fallar" }).click();

    await expect
      .element(
        screen.getByText(
          "No se pudo completar la verificación. Inténtalo de nuevo.",
        ),
      )
      .toBeVisible();
    await screen.getByRole("button", { name: "Consultar" }).click();
    await expect
      .element(screen.getByText("Completa la verificación"))
      .toBeVisible();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("sends a Turnstile token in the same field", async () => {
    const { onSubmit, screen: rendering } = renderForm({
      provider: "turnstile",
      siteKey: "site-t",
    });
    const screen = await rendering;

    await expect.element(screen.getByText("Turnstile site-t")).toBeVisible();
    await fillDocument(screen);
    await screen.getByRole("button", { name: "Resolver" }).click();
    await screen.getByRole("button", { name: "Consultar" }).click();

    await expect.poll(() => onSubmit.mock.calls.length).toBe(1);
    expect(onSubmit.mock.calls[0]?.[0]).toMatchObject({ captcha: "t-token" });
    expect(widget.reset).toHaveBeenCalledOnce();
  });
});
