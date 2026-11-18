import dayjs from "dayjs";
import { describe, expect, it } from "vitest";

import { usageMonths } from "./partnerTenantListState";

describe("usageMonths", () => {
  it("offers this month and the 11 before, newest first", () => {
    const months = usageMonths(dayjs("2026-02-15"));

    expect(months).toHaveLength(12);
    expect(months[0]).toBe("2026-02");
    expect(months[1]).toBe("2026-01");
    expect(months[11]).toBe("2025-03");
  });
});
