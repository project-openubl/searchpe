import { describe, expect, it } from "vitest";

import { formatBytes } from "./exportPresentation";

describe("formatBytes", () => {
  it("picks a readable unit", () => {
    expect(formatBytes(512)).toBe("512 B");
    expect(formatBytes(1536)).toBe("1.5 KB");
    expect(formatBytes(5 * 1024 * 1024)).toBe("5 MB");
  });

  it("is empty without a size", () => {
    expect(formatBytes(undefined)).toBe("");
  });
});
