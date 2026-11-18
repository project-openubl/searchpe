import { describe, expect, it } from "vitest";

import {
  isOverdue,
  subjectDocTypeLabel,
} from "./dataSubjectRequestPresentation";

describe("subjectDocTypeLabel", () => {
  it("names catalog 06 codes", () => {
    expect(subjectDocTypeLabel("1")).toBe("DNI");
    expect(subjectDocTypeLabel("Z")).toBe("Z");
  });
});

describe("isOverdue", () => {
  const now = new Date("2026-03-10T12:00:00Z");

  it("flags open requests past their term", () => {
    expect(
      isOverdue({ status: "open", responseDueAt: "2026-03-09T00:00:00Z", now }),
    ).toBe(true);
  });

  it("never flags closed or in-term requests", () => {
    expect(
      isOverdue({
        status: "closed",
        responseDueAt: "2026-03-09T00:00:00Z",
        now,
      }),
    ).toBe(false);
    expect(
      isOverdue({ status: "open", responseDueAt: "2026-03-20T00:00:00Z", now }),
    ).toBe(false);
  });
});
