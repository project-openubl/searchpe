import { describe, expect, it } from "vitest";

import { certificateDaysLeft, certificateStanding } from "./certificates";

describe("certificate expiry helpers", () => {
  const now = new Date("2026-11-17T12:00:00Z");

  it("counts whole days left, negative once expired", () => {
    expect(
      certificateDaysLeft({ validUntil: "2026-11-27T12:00:00Z", now }),
    ).toBe(10);
    expect(
      certificateDaysLeft({ validUntil: "2026-11-16T12:00:00Z", now }),
    ).toBe(-1);
    expect(certificateDaysLeft({ validUntil: undefined, now })).toBeUndefined();
  });

  it("warns from 30 days before expiry", () => {
    expect(certificateStanding(31)).toBe("valid");
    expect(certificateStanding(30)).toBe("expiring");
    expect(certificateStanding(0)).toBe("expiring");
    expect(certificateStanding(-1)).toBe("expired");
    expect(certificateStanding(undefined)).toBeUndefined();
  });
});
