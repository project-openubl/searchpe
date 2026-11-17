import axios, {
  AxiosError,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from "axios";
import type { User, UserManager } from "oidc-client-ts";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { userManager } from "@app/oidc";

import { addAuthInterceptors, initInterceptors, publicAxios } from "./apiInit";

vi.mock(import("@app/oidc"), () => ({
  userManager: {
    getUser: vi.fn(),
    signinSilent: vi.fn(),
    signinRedirect: vi.fn(),
  } as unknown as UserManager,
  currentPageState: () => "/organizaciones",
}));

const user = (accessToken: string) =>
  ({ access_token: accessToken }) as unknown as User;

const ok = (config: InternalAxiosRequestConfig): AxiosResponse => ({
  data: { ok: true },
  status: 200,
  statusText: "OK",
  headers: {},
  config,
});

const unauthorized = (config: InternalAxiosRequestConfig) =>
  new AxiosError("Unauthorized", "ERR_BAD_REQUEST", config, undefined, {
    data: "",
    status: 401,
    statusText: "Unauthorized",
    headers: {},
    config,
  });

/** An axios instance whose "server" accepts only `validToken`, recording what it was sent. */
const instanceAccepting = (validToken: string) => {
  const seen: (string | undefined)[] = [];
  const instance = axios.create({
    adapter: (config) => {
      const authorization = config.headers.Authorization as string | undefined;
      seen.push(authorization);
      return authorization === `Bearer ${validToken}`
        ? Promise.resolve(ok(config))
        : Promise.reject(unauthorized(config));
    },
  });
  return { instance, seen };
};

beforeEach(() => {
  vi.mocked(userManager.getUser).mockReset();
  vi.mocked(userManager.signinSilent).mockReset();
  vi.mocked(userManager.signinRedirect).mockReset().mockResolvedValue();
});

describe("publicAxios", () => {
  it("never sends the user's token, even with a session", async () => {
    vi.mocked(userManager.getUser).mockResolvedValue(user("secret"));
    initInterceptors();

    let authorization: unknown = "not called";
    await publicAxios.get("/api/xhandler/v1/public/documents/abc", {
      adapter: (config) => {
        authorization = config.headers.Authorization;
        return Promise.resolve(ok(config));
      },
    });

    expect(authorization).toBeUndefined();
  });
});

describe("addAuthInterceptors", () => {
  it("sends the stored user's token", async () => {
    vi.mocked(userManager.getUser).mockResolvedValue(user("valid"));
    const { instance, seen } = instanceAccepting("valid");
    addAuthInterceptors(instance, { onRenewalFailure: "login" });

    await instance.get("/api/xhandler/v1/tenants");

    expect(seen).toEqual(["Bearer valid"]);
  });

  it("renews once for concurrent 401s and retries each request with the new token", async () => {
    vi.mocked(userManager.getUser).mockResolvedValue(user("expired"));
    vi.mocked(userManager.signinSilent).mockImplementation(
      () =>
        new Promise((resolve) => setTimeout(() => resolve(user("fresh")), 20)),
    );
    const { instance, seen } = instanceAccepting("fresh");
    addAuthInterceptors(instance, { onRenewalFailure: "login" });

    const responses = await Promise.all([
      instance.get("/a"),
      instance.get("/b"),
      instance.get("/c"),
    ]);

    expect(responses.map((response) => response.status)).toEqual([
      200, 200, 200,
    ]);
    expect(userManager.signinSilent).toHaveBeenCalledOnce();
    expect(seen.filter((token) => token === "Bearer fresh")).toHaveLength(3);
  });

  it("rejects a 401 without a stored user and never renews, logs in or out", async () => {
    vi.mocked(userManager.getUser).mockResolvedValue(null);
    const { instance } = instanceAccepting("valid");
    addAuthInterceptors(instance, { onRenewalFailure: "login" });

    await expect(instance.get("/a")).rejects.toMatchObject({
      response: { status: 401 },
    });
    expect(userManager.signinSilent).not.toHaveBeenCalled();
    expect(userManager.signinRedirect).not.toHaveBeenCalled();
  });

  it("rejects a second 401 after the retry without renewing again", async () => {
    vi.mocked(userManager.getUser).mockResolvedValue(user("expired"));
    vi.mocked(userManager.signinSilent).mockResolvedValue(user("also-bad"));
    const { instance, seen } = instanceAccepting("never");
    addAuthInterceptors(instance, { onRenewalFailure: "reject" });

    await expect(instance.get("/api/searchpe/whoami")).rejects.toMatchObject({
      response: { status: 401 },
    });
    expect(userManager.signinSilent).toHaveBeenCalledOnce();
    expect(seen).toEqual(["Bearer expired", "Bearer also-bad"]);
  });

  it("sends xhandler's user to log in again when the renewal fails", async () => {
    vi.mocked(userManager.getUser).mockResolvedValue(user("expired"));
    vi.mocked(userManager.signinSilent).mockRejectedValue(
      new Error("login_required"),
    );
    const { instance } = instanceAccepting("valid");
    addAuthInterceptors(instance, { onRenewalFailure: "login" });

    await expect(instance.get("/a")).rejects.toBeInstanceOf(AxiosError);
    expect(userManager.signinRedirect).toHaveBeenCalledExactlyOnceWith({
      url_state: "/organizaciones",
    });
  });

  it("only fails searchpe's request when the renewal fails", async () => {
    vi.mocked(userManager.getUser).mockResolvedValue(user("expired"));
    vi.mocked(userManager.signinSilent).mockRejectedValue(
      new Error("login_required"),
    );
    const { instance } = instanceAccepting("valid");
    addAuthInterceptors(instance, { onRenewalFailure: "reject" });

    await expect(instance.get("/api/searchpe/whoami")).rejects.toMatchObject({
      response: { status: 401 },
    });
    expect(userManager.signinRedirect).not.toHaveBeenCalled();
  });
});
