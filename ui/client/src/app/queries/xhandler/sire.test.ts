import { describe, expect, it } from "vitest";

import {
  isSirePeriod,
  isSireReplaceable,
  isSireStatusInProgress,
} from "./sire";

describe("SIRE period helpers", () => {
  it("polls only while a job or SUNAT moves the period on", () => {
    expect(isSireStatusInProgress("requested")).toBe(true);
    expect(isSireStatusInProgress("submitting")).toBe(true);
    expect(isSireStatusInProgress("submitted")).toBe(true);
    expect(isSireStatusInProgress("proposal_ready")).toBe(false);
    expect(isSireStatusInProgress("credentials")).toBe(false);
  });

  it("replaces only a reconciled period the server accepts", () => {
    expect(
      isSireReplaceable({ status: "proposal_ready", reconciliation: {} }),
    ).toBe(true);
    expect(isSireReplaceable({ status: "unknown", reconciliation: {} })).toBe(
      true,
    );
    expect(isSireReplaceable({ status: "proposal_ready" })).toBe(false);
    expect(isSireReplaceable({ status: "accepted", reconciliation: {} })).toBe(
      false,
    );
  });

  it("checks the AAAAMM period", () => {
    expect(isSirePeriod("202612")).toBe(true);
    expect(isSirePeriod("202613")).toBe(false);
    expect(isSirePeriod("2026-12")).toBe(false);
  });
});
