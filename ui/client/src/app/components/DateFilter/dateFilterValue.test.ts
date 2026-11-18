import { describe, expect, it } from "vitest";

import {
  formatFilterDate,
  parseFilterDate,
  toFilterIsoDate,
} from "./dateFilterValue";

describe("date filter values", () => {
  it("shows an ISO date the Peruvian way", () => {
    expect(formatFilterDate("2026-03-09")).toBe("09/03/2026");
    expect(formatFilterDate("")).toBe("");
  });

  it("reads DD/MM/YYYY into the ISO date the API takes", () => {
    expect(toFilterIsoDate("9/3/2026")).toBe("2026-03-09");
    expect(toFilterIsoDate("31/12/2025")).toBe("2025-12-31");
  });

  it("rejects text that isn't a real date", () => {
    expect(toFilterIsoDate("31/02/2026")).toBeUndefined();
    expect(toFilterIsoDate("2026-03-09")).toBeUndefined();
    expect(Number.isNaN(parseFilterDate("hoy").getTime())).toBe(true);
  });
});
