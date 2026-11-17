import { describe, expect, it } from "vitest";

import { isNumeroDocumento } from "./contribuyentes";

describe("isNumeroDocumento", () => {
  it("accepts an 11-digit RUC and an 8-digit DNI", () => {
    expect(isNumeroDocumento("20123456789")).toBe(true);
    expect(isNumeroDocumento("12345678")).toBe(true);
  });

  it("rejects anything else", () => {
    expect(isNumeroDocumento("1234567")).toBe(false);
    expect(isNumeroDocumento("2012345678")).toBe(false);
    expect(isNumeroDocumento("2012345678A")).toBe(false);
    expect(isNumeroDocumento("")).toBe(false);
  });
});
