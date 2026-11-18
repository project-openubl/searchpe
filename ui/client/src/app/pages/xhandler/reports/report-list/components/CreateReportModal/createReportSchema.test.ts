import { describe, expect, it } from "vitest";

import { createReportSchema, toReportPeriod } from "./createReportSchema";

describe("createReportSchema", () => {
  it("accepts a register, a format and a month", async () => {
    await expect(
      createReportSchema.validate({
        register: "sales",
        format: "xlsx",
        month: "2026-03",
      }),
    ).resolves.toBeTruthy();
  });

  it("refuses values the API doesn't take", async () => {
    await expect(
      createReportSchema.validate({
        register: "inventory",
        format: "xlsx",
        month: "2026-03",
      }),
    ).rejects.toThrow("Elige el registro.");
    await expect(
      createReportSchema.validate({
        register: "sales",
        format: "xlsx",
        month: "2026-13",
      }),
    ).rejects.toThrow(/periodo válido/);
  });

  it("sends the period as YYYYMM", () => {
    expect(toReportPeriod("2026-03")).toBe("202603");
  });
});
