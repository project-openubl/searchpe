import { describe, expect, it } from "vitest";

import {
  buildOpenublEnv,
  rewriteSearchpeApiPath,
  rewriteXhandlerApiPath,
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
