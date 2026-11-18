import { describe, expect, it } from "vitest";

import { quotaPercent } from "./billing";

describe("quotaPercent", () => {
  it("is the share used, capped at 100", () => {
    expect(quotaPercent({ used: 80, limit: 100 })).toBe(80);
    expect(quotaPercent({ used: 150, limit: 100 })).toBe(100);
    expect(quotaPercent({ used: undefined, limit: 50 })).toBe(0);
  });

  it("has no share for an unlimited quota", () => {
    expect(quotaPercent({ used: 10, limit: null })).toBeUndefined();
    expect(quotaPercent({ used: 10, limit: undefined })).toBeUndefined();
  });
});
