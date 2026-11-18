import { describe, expect, it, vi } from "vitest";
import { render } from "vitest-browser-react";

import type { WebhookView } from "@app/client/xhandler";
import { useNotifications } from "@app/hooks/useNotifications";
import { useWebhookCreateMutation } from "@app/queries/xhandler/webhooks";

import { CreateWebhookModal } from "./CreateWebhookModal";

vi.mock(import("@app/hooks/useNotifications"), () => ({
  useNotifications: vi.fn(),
}));
vi.mock(import("@app/queries/xhandler/webhooks"), () => ({
  WEBHOOK_EVENT_TYPES: [
    "document.accepted",
    "document.rejected",
    "document.rejected_after_delivery",
    "document.exception",
    "document.voided",
    "document.void_rejected",
    "document.delayed",
    "conformity.objected",
  ] as const,
  useWebhookCreateMutation: vi.fn(),
}));

/**
 * A mutation that answers like the API: 201 with the one-time secret, or without it when the
 * Idempotency-Key was already used (a replay).
 */
const setUp = (secret: string | null = "whsec_abc123") => {
  const mutate = vi.fn(
    (
      variables: {
        body: { url: string; events: string[] };
        idempotencyKey: string;
      },
      options?: { onSuccess?: (webhook: WebhookView) => void },
    ) =>
      options?.onSuccess?.({
        id: "w1",
        url: variables.body.url,
        secret: secret ?? undefined,
      }),
  );
  vi.mocked(useNotifications).mockReturnValue({
    addNotification: vi.fn(),
  } as unknown as ReturnType<typeof useNotifications>);
  vi.mocked(useWebhookCreateMutation).mockReturnValue({
    mutate,
    isPending: false,
  } as unknown as ReturnType<typeof useWebhookCreateMutation>);
  return mutate;
};

describe("CreateWebhookModal", () => {
  it("refuses a plain-http URL", async () => {
    const mutate = setUp();
    const screen = await render(
      <CreateWebhookModal isOpen onClose={vi.fn()} />,
    );
    await screen
      .getByRole("textbox", { name: "URL", exact: true })
      .fill("http://erp.pe/hook");
    await screen.getByRole("button", { name: "Crear webhook" }).click();
    await expect
      .element(screen.getByText("La URL debe usar https"))
      .toBeVisible();
    expect(mutate).not.toHaveBeenCalled();
  });

  it("shows the signing secret once after creating", async () => {
    const mutate = setUp();
    const screen = await render(
      <CreateWebhookModal isOpen onClose={vi.fn()} />,
    );
    await screen
      .getByRole("textbox", { name: "URL", exact: true })
      .fill("https://erp.pe/hook");
    await screen.getByRole("button", { name: "Crear webhook" }).click();

    await expect.poll(() => mutate.mock.calls.length).toBe(1);
    expect(mutate.mock.calls[0][0]).toEqual({
      body: { url: "https://erp.pe/hook", events: ["*"] },
      idempotencyKey: expect.stringMatching(/^[0-9a-f-]{36}$/),
    });
    await expect
      .element(
        screen.getByText("Copia el secreto ahora: no se volverá a mostrar."),
      )
      .toBeVisible();
    await expect
      .element(screen.getByRole("textbox", { name: "Secreto del webhook" }))
      .toHaveValue("whsec_abc123");
  });

  it("retries the same submission with the same Idempotency-Key", async () => {
    const mutate = vi.fn();
    setUp();
    vi.mocked(useWebhookCreateMutation).mockReturnValue({
      mutate,
      isPending: false,
    } as unknown as ReturnType<typeof useWebhookCreateMutation>);
    const screen = await render(
      <CreateWebhookModal isOpen onClose={vi.fn()} />,
    );
    await screen
      .getByRole("textbox", { name: "URL", exact: true })
      .fill("https://erp.pe/hook");
    await screen.getByRole("button", { name: "Crear webhook" }).click();
    await screen.getByRole("button", { name: "Crear webhook" }).click();

    await expect.poll(() => mutate.mock.calls.length).toBe(2);
    expect(mutate.mock.calls[1][0].idempotencyKey).toBe(
      mutate.mock.calls[0][0].idempotencyKey,
    );
  });

  it("says the secret was already shown when the answer is a replay", async () => {
    setUp(null);
    const screen = await render(
      <CreateWebhookModal isOpen onClose={vi.fn()} />,
    );
    await screen
      .getByRole("textbox", { name: "URL", exact: true })
      .fill("https://erp.pe/hook");
    await screen.getByRole("button", { name: "Crear webhook" }).click();

    await expect
      .element(screen.getByText("El secreto ya se mostró al crear el webhook"))
      .toBeVisible();
  });
});
