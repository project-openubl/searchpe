import { describe, expect, it } from "vitest";

import { formatTotal, tipoComprobanteLabel } from "./documentPresentation";

describe("tipoComprobanteLabel", () => {
  it("names the SUNAT types in Spanish", () => {
    expect(tipoComprobanteLabel("01")).toBe("Factura");
    expect(tipoComprobanteLabel("03")).toBe("Boleta de venta");
    expect(tipoComprobanteLabel("07")).toBe("Nota de crédito");
    expect(tipoComprobanteLabel("RA")).toBe("Comunicación de baja");
  });

  it("shows an unknown code as returned", () => {
    expect(tipoComprobanteLabel("99")).toBe("99");
    expect(tipoComprobanteLabel(undefined)).toBe("-");
  });
});

describe("formatTotal", () => {
  it("formats soles the Peruvian way", () => {
    expect(formatTotal({ total: 1180, moneda: "PEN" })).toMatch(
      /S\/\s?1,180\.00/,
    );
  });

  it("reads amounts sent as text", () => {
    expect(formatTotal({ total: "50", moneda: "USD" })).toMatch(/50\.00/);
  });

  it("shows a dash without an amount", () => {
    expect(formatTotal({ total: undefined, moneda: "PEN" })).toBe("-");
    expect(formatTotal({ total: "n/a", moneda: "PEN" })).toBe("-");
  });

  it("keeps a currency that isn't ISO 4217 before the amount", () => {
    expect(formatTotal({ total: 5, moneda: "XX1" })).toBe("XX1 5.00");
  });
});
