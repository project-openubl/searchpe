import { describe, expect, it } from "vitest";

import {
  formatTotal,
  publicDocumentStatusLabel,
  tipoComprobanteLabel,
} from "./documentPresentation";

describe("tipoComprobanteLabel", () => {
  it("names SUNAT's catalog 01 codes in Spanish", () => {
    expect(tipoComprobanteLabel("01")).toBe("Factura");
    expect(tipoComprobanteLabel("03")).toBe("Boleta de venta");
    expect(tipoComprobanteLabel("07")).toBe("Nota de crédito");
  });

  it("shows an unknown code as sent", () => {
    expect(tipoComprobanteLabel("99")).toBe("99");
  });
});

describe("publicDocumentStatusLabel", () => {
  it("labels the public statuses in Spanish", () => {
    expect(publicDocumentStatusLabel("aceptado")).toBe("Aceptado por SUNAT");
    expect(publicDocumentStatusLabel("documento_de_prueba")).toBe(
      "Documento de prueba (sin validez tributaria)",
    );
  });

  it("shows a missing status as a dash", () => {
    expect(publicDocumentStatusLabel(undefined)).toBe("-");
  });
});

describe("formatTotal", () => {
  it("formats the amount in its currency for es-PE", () => {
    const expected = new Intl.NumberFormat("es-PE", {
      style: "currency",
      currency: "PEN",
    }).format(1180);
    expect(formatTotal(1180, "PEN")).toBe(expected);
    expect(expected).toContain("1,180.00");
  });

  it("falls back to the code and the amount for a non-ISO currency", () => {
    expect(formatTotal(10, "XXXX")).toBe("XXXX 10.00");
  });

  it("shows a missing total as a dash", () => {
    expect(formatTotal(undefined, "PEN")).toBe("-");
  });
});
