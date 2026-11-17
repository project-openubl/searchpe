import { describe, expect, it } from "vitest";

import { tenantRoleAllows } from "./useTenantRole";

describe("tenantRoleAllows", () => {
  it("lets a stronger role do what a weaker one may", () => {
    expect(tenantRoleAllows({ role: "owner", minimum: "operator" })).toBe(true);
    expect(tenantRoleAllows({ role: "operator", minimum: "operator" })).toBe(
      true,
    );
    expect(tenantRoleAllows({ role: "viewer", minimum: "operator" })).toBe(
      false,
    );
  });

  it("lets support only read", () => {
    expect(tenantRoleAllows({ role: "support", minimum: "viewer" })).toBe(true);
    expect(tenantRoleAllows({ role: "support", minimum: "operator" })).toBe(
      false,
    );
  });

  it("allows nothing without a known role", () => {
    expect(tenantRoleAllows({ role: undefined, minimum: "viewer" })).toBe(
      false,
    );
    expect(tenantRoleAllows({ role: "billing", minimum: "viewer" })).toBe(
      false,
    );
  });
});
