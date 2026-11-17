import { describe, expect, it, vi } from "vitest";

import type { IAuthContext } from "./AuthContext";
import { requireAuthentication } from "./requireAuthentication";

const authContext = (overrides: Partial<IAuthContext> = {}): IAuthContext => ({
  user: null,
  isAuthenticated: () => false,
  isLoading: false,
  error: null,
  login: vi.fn(),
  logout: vi.fn(),
  ...overrides,
});

const location = { href: "/organizaciones?pagina=2" };

/** Settles within a few ticks, or reports that it is still pending. */
const settlesSoon = (promise: Promise<unknown>) =>
  Promise.race([
    promise.then(
      () => "settled",
      () => "settled",
    ),
    new Promise((resolve) => setTimeout(() => resolve("pending"), 20)),
  ]);

describe("requireAuthentication", () => {
  it("lets an authenticated user through", () => {
    const auth = authContext({ isAuthenticated: () => true });

    expect(
      requireAuthentication({ context: { auth }, location, preload: false }),
    ).toBeUndefined();
    expect(auth.login).not.toHaveBeenCalled();
  });

  it("starts the login back to the page and holds the route", async () => {
    const auth = authContext();

    const result = requireAuthentication({
      context: { auth },
      location,
      preload: false,
    });

    expect(auth.login).toHaveBeenCalledExactlyOnceWith(
      "/organizaciones?pagina=2",
    );
    expect(result).toBeInstanceOf(Promise);
    expect(await settlesSoon(result!)).toBe("pending");
  });

  it("never starts a login while preloading", () => {
    const auth = authContext();

    expect(
      requireAuthentication({ context: { auth }, location, preload: true }),
    ).toBeUndefined();
    expect(auth.login).not.toHaveBeenCalled();
  });

  it("throws the OIDC error instead of looping on the login", () => {
    const error = new Error("Keycloak no responde");
    const auth = authContext({ error });

    expect(() =>
      requireAuthentication({ context: { auth }, location, preload: false }),
    ).toThrow(error);
    expect(auth.login).not.toHaveBeenCalled();
  });
});
