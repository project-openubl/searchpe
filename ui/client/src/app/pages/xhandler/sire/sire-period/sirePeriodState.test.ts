import dayjs from "dayjs";
import { describe, expect, it } from "vitest";

import { sirePeriods } from "./sirePeriodState";

describe("sirePeriods", () => {
  it("starts at last month, the one being declared", () => {
    const periods = sirePeriods(dayjs("2026-01-10"));

    expect(periods).toHaveLength(24);
    expect(periods[0]).toBe("202512");
    expect(periods[23]).toBe("202401");
  });
});
