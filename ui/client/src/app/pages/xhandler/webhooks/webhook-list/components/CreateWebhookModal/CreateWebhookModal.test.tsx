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

/** A mutation that answers like the API: 201 with the one-time secret. */
const setUp = () => {
  const mutate = vi.fn(
    (
      body: { url: string; events: string[] },
      options?: { onSuccess?: (webhook: WebhookView) => void },
    ) =>
      options?.onSuccess?.({ id: "w1", url: body.url, secret: "whsec_abc123" }),
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
      url: "https://erp.pe/hook",
      events: ["*"],
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
});
