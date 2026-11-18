import { describe, expect, it } from "vitest";

import { isHttpsUrl } from "./url";

describe("isHttpsUrl", () => {
  it("accepts an https address", () => {
    expect(isHttpsUrl("https://bodega.pe/reclamos")).toBe(true);
  });

  it.each([
    "",
    "http://bodega.pe/reclamos",
    "bodega.pe/reclamos",
    "https://user:pass@bodega.pe/",
    "javascript:alert(1)",
  ])("rejects %j", (value) => {
    expect(isHttpsUrl(value)).toBe(false);
  });
});
