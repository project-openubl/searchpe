import { describe, expect, it } from "vitest";

import { fileToBase64 } from "./evidence";

describe("fileToBase64", () => {
  it("encodes the file's bytes", async () => {
    const pdf = new Blob(["%PDF-1.4"], { type: "application/pdf" });

    expect(await fileToBase64(pdf)).toBe(btoa("%PDF-1.4"));
  });

  it("encodes files larger than one chunk", async () => {
    const bytes = new Uint8Array(70000).fill(65);

    expect(await fileToBase64(new Blob([bytes]))).toBe(btoa("A".repeat(70000)));
  });
});
