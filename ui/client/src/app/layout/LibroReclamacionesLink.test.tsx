import { describe, expect, it, vi } from "vitest";
import { render } from "vitest-browser-react";

import { HeaderApp } from "./HeaderApp";
import { PublicLayout } from "./PublicLayout";

const env = vi.hoisted(() => ({
  ENV: { LIBRO_RECLAMACIONES_URL: undefined as string | undefined },
}));

vi.mock(import("@app/env"), () => env as never);
vi.mock(import("@tanstack/react-router"), () => ({
  useNavigate: vi.fn(() => vi.fn()),
}));
vi.mock(import("@app/hooks/useAuth"), () => ({
  useAuth: vi.fn(
    () =>
      ({
        isAuthenticated: () => true,
        user: { username: "alice" },
        logout: vi.fn(),
      }) as never,
  ),
}));
vi.mock(import("@app/hooks/useApiStatus"), () => ({
  useApiStatus: vi.fn(() => ({ xhandler: {} }) as never),
}));

const LINK = { name: "Libro de Reclamaciones" };

describe("LibroReclamacionesLink", () => {
  it("is always in the public shell when configured", async () => {
    env.ENV.LIBRO_RECLAMACIONES_URL = "https://openubl.example/reclamaciones";
    const screen = await render(
      <PublicLayout>Comprobante del cliente</PublicLayout>,
    );

    await expect
      .element(screen.getByRole("link", LINK))
      .toHaveAttribute("href", "https://openubl.example/reclamaciones");
  });

  it("is always in the app shell when configured", async () => {
    env.ENV.LIBRO_RECLAMACIONES_URL = "https://openubl.example/reclamaciones";
    const screen = await render(
      <HeaderApp isSidebarOpen onSidebarToggle={vi.fn()} />,
    );

    await expect
      .element(screen.getByRole("link", LINK))
      .toHaveAttribute("target", "_blank");
  });

  it.each([undefined, "", "http://openubl.example/reclamaciones"])(
    "is hidden when the address is %j",
    async (url) => {
      env.ENV.LIBRO_RECLAMACIONES_URL = url;
      const screen = await render(
        <PublicLayout>Comprobante del cliente</PublicLayout>,
      );

      await expect
        .element(screen.getByText("Comprobante del cliente"))
        .toBeVisible();
      expect(screen.getByRole("link", LINK).elements()).toHaveLength(0);
    },
  );
});
