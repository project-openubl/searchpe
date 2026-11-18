import { describe, expect, it, vi } from "vitest";
import { render } from "vitest-browser-react";

import { useNotifications } from "@app/hooks/useNotifications";
import {
  sireReconciliation,
  useReplaceSireProposalMutation,
} from "@app/queries/xhandler/sire";

import { SireReplaceModal } from "./SireReplaceModal";

vi.mock(import("@app/hooks/useNotifications"), () => ({
  useNotifications: vi.fn(),
}));
vi.mock(import("@app/queries/xhandler/sire"), () => ({
  sireReconciliation: vi.fn(),
  useReplaceSireProposalMutation: vi.fn(),
}));

const PATH = { companyId: "c1", book: "rvie" as const, period: "202610" };

const setUp = ({ matches }: { matches: boolean }) => {
  const mutate = vi.fn();
  vi.mocked(useNotifications).mockReturnValue({
    addNotification: vi.fn(),
  } as unknown as ReturnType<typeof useNotifications>);
  vi.mocked(useReplaceSireProposalMutation).mockReturnValue({
    mutate,
    isPending: false,
  } as unknown as ReturnType<typeof useReplaceSireProposalMutation>);
  vi.mocked(sireReconciliation).mockReturnValue({ matches });
  return mutate;
};

describe("SireReplaceModal", () => {
  it("requires accepting the differences and the SOL check after an unknown outcome", async () => {
    const mutate = setUp({ matches: false });
    const screen = await render(
      <SireReplaceModal
        path={PATH}
        period={{ status: "unknown", reconciliation: {} }}
        onClose={vi.fn()}
      />,
    );

    await screen.getByRole("button", { name: "Enviar reemplazo" }).click();
    await expect
      .element(screen.getByText("Confirma que aceptas las diferencias"))
      .toBeVisible();
    await expect
      .element(screen.getByText("Confirma que verificaste el envío en SOL"))
      .toBeVisible();
    expect(mutate).not.toHaveBeenCalled();

    await screen
      .getByRole("checkbox", { name: /acepto las diferencias/ })
      .click();
    await screen
      .getByRole("checkbox", { name: /no recibió el reemplazo/ })
      .click();
    await screen.getByRole("button", { name: "Enviar reemplazo" }).click();

    await expect.poll(() => mutate.mock.calls.length).toBe(1);
    expect(mutate.mock.calls[0][0]).toEqual({
      ...PATH,
      body: { acknowledge_differences: true, confirm_not_received: true },
    });
  });

  it("asks nothing more when the proposal matches", async () => {
    const mutate = setUp({ matches: true });
    const screen = await render(
      <SireReplaceModal
        path={PATH}
        period={{ status: "proposal_ready", reconciliation: {} }}
        onClose={vi.fn()}
      />,
    );

    await expect.element(screen.getByRole("checkbox")).not.toBeInTheDocument();
    await screen.getByRole("button", { name: "Enviar reemplazo" }).click();

    await expect.poll(() => mutate.mock.calls.length).toBe(1);
    expect(mutate.mock.calls[0][0]).toEqual({
      ...PATH,
      body: { acknowledge_differences: false, confirm_not_received: false },
    });
  });
});
