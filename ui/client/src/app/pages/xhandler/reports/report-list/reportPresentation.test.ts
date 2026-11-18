import { describe, expect, it } from "vitest";

import { formatPeriod } from "./reportPresentation";

describe("formatPeriod", () => {
  it("reads YYYYMM as month/year", () => {
    expect(formatPeriod("202603")).toBe("03/2026");
  });

  it("shows anything else as returned", () => {
    expect(formatPeriod("2026")).toBe("2026");
    expect(formatPeriod(undefined)).toBe("-");
  });
});
