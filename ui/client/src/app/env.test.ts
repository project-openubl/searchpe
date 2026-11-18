import { describe, expect, it } from "vitest";

import {
  buildOpenublEnv,
  decodeEnv,
  encodeEnv,
  hasUnsafePathSegments,
  resolveApiProxyPath,
  rewriteSearchpeApiPath,
  rewriteXhandlerApiPath,
  SEARCHPE_API_ROUTE,
  SEARCHPE_HEALTH_PATH,
  XHANDLER_API_ROUTE,
} from "@openubl-ui/common";

describe("proxy path rewrites", () => {
  it("sends /api/xhandler to xhandler-service's /api", () => {
    expect(rewriteXhandlerApiPath("/api/xhandler/v1/status")).toBe(
      "/api/v1/status",
    );
    expect(rewriteXhandlerApiPath("/api/xhandlerx/v1")).toBe(
      "/api/xhandlerx/v1",
    );
  });

  it("sends /api/searchpe to searchpe's /api, and its health check to /q/health", () => {
    expect(rewriteSearchpeApiPath("/api/searchpe/whoami")).toBe("/api/whoami");
    expect(rewriteSearchpeApiPath("/api/searchpe/q/health/ready")).toBe(
      "/q/health/ready",
    );
  });
});

describe("hasUnsafePathSegments", () => {
  it("accepts ordinary API paths, whatever their query says", () => {
    expect(hasUnsafePathSegments("/api/xhandler/v1/tenants")).toBe(false);
    expect(
      hasUnsafePathSegments("/api/searchpe/contribuyentes?filter=a%2Fb/../c"),
    ).toBe(false);
    expect(hasUnsafePathSegments("/api/xhandler/v1/files/a.xml")).toBe(false);
  });

  it.each([
    "/api/xhandler/../q/metrics",
    "/api/xhandler/./v1/tenants",
    "/api/xhandler/v1/..",
    "/api/xhandler/%2e%2e/q/metrics",
    "/api/xhandler/%2E./q/metrics",
    "/api/xhandler/v1%2f..%2fq",
    "/api/xhandler/v1%2F",
    "/api/xhandler/v1%5c..",
    "/api/xhandler/v1\\..\\q",
    "/api/xhandler/%252e%252e/q/metrics",
    "/api/xhandler/%E0%A4%A",
  ])("rejects %s", (url) => {
    expect(hasUnsafePathSegments(url)).toBe(true);
  });
});

describe("resolveApiProxyPath", () => {
  it("maps a client path to the backend's /api/, query included", () => {
    expect(
      resolveApiProxyPath(
        "/api/xhandler/v1/jobs?state=failed",
        XHANDLER_API_ROUTE,
      ),
    ).toBe("/api/v1/jobs?state=failed");
    expect(
      resolveApiProxyPath("/api/searchpe/whoami", SEARCHPE_API_ROUTE),
    ).toBe("/api/whoami");
  });

  it("allows searchpe's readiness check and nothing else outside /api/", () => {
    expect(resolveApiProxyPath(SEARCHPE_HEALTH_PATH, SEARCHPE_API_ROUTE)).toBe(
      "/q/health/ready",
    );
    expect(
      resolveApiProxyPath("/api/searchpe/q/health", SEARCHPE_API_ROUTE),
    ).toBeUndefined();
    expect(
      resolveApiProxyPath("/api/searchpe/q/health/live", SEARCHPE_API_ROUTE),
    ).toBeUndefined();
    expect(
      resolveApiProxyPath("/api/xhandler/q/health/ready", XHANDLER_API_ROUTE),
    ).toBe("/api/q/health/ready");
  });

  it("rejects the bare prefix, another prefix and unsafe paths", () => {
    expect(
      resolveApiProxyPath("/api/xhandler", XHANDLER_API_ROUTE),
    ).toBeUndefined();
    expect(
      resolveApiProxyPath("/api/xhandlerx/v1", XHANDLER_API_ROUTE),
    ).toBeUndefined();
    expect(
      resolveApiProxyPath("/api/searchpe/whoami", XHANDLER_API_ROUTE),
    ).toBeUndefined();
    expect(
      resolveApiProxyPath(
        "/api/xhandler/v1/../../q/metrics",
        XHANDLER_API_ROUTE,
      ),
    ).toBeUndefined();
  });
});

describe("encodeEnv", () => {
  it("sends the browser only the allowlisted keys", () => {
    const env = buildOpenublEnv({
      XHANDLER_API_URL: "http://xhandler:8080",
      SEARCHPE_API_URL: "http://searchpe:8080",
      PORT: "8080",
    });
    const sent = decodeEnv(
      encodeEnv({ ...env, SOME_SECRET: "s3cr3t" }),
    ) as Record<string, unknown>;

    expect(Object.keys(sent).sort()).toEqual([
      "NODE_ENV",
      "OIDC_CLIENT_ID",
      "OIDC_SCOPE",
      "OIDC_SERVER_URL",
      "SEARCHPE_ENABLED",
      "VERSION",
    ]);
    expect(sent.SEARCHPE_ENABLED).toBe("true");
  });

  it("sends the platform's Libro de Reclamaciones when set", () => {
    const env = buildOpenublEnv({
      LIBRO_RECLAMACIONES_URL: "https://openubl.example/reclamaciones",
    });
    const sent = decodeEnv(encodeEnv(env)) as Record<string, unknown>;

    expect(sent.LIBRO_RECLAMACIONES_URL).toBe(
      "https://openubl.example/reclamaciones",
    );
  });
});

describe("buildOpenublEnv", () => {
  it("enables searchpe only when its URL is set", () => {
    expect(buildOpenublEnv({}).SEARCHPE_ENABLED).toBe("false");
    expect(
      buildOpenublEnv({ SEARCHPE_API_URL: "http://searchpe:8080" })
        .SEARCHPE_ENABLED,
    ).toBe("true");
  });

  it("keeps the flag the server sent, without the URL", () => {
    expect(buildOpenublEnv({ SEARCHPE_ENABLED: "true" }).SEARCHPE_ENABLED).toBe(
      "true",
    );
  });

  it("defaults to the xhandler-ui public client", () => {
    expect(buildOpenublEnv({}).OIDC_CLIENT_ID).toBe("xhandler-ui");
  });
});
