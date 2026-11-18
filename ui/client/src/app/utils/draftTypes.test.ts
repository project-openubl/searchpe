import { describe, expect, it } from "vitest";

import { draftTypeLabel, isDraftTipo, isNoteTipo } from "./draftTypes";

describe("draftTypes", () => {
  it("labels the types the console issues", () => {
    expect(draftTypeLabel({ kind: "Invoice", tipo_comprobante: "01" })).toBe(
      "Factura",
    );
    expect(draftTypeLabel({ kind: "Invoice", tipo_comprobante: "03" })).toBe(
      "Boleta de venta",
    );
    expect(draftTypeLabel({ kind: "CreditNote", tipo_comprobante: "07" })).toBe(
      "Nota de crédito",
    );
  });

  it("shows another kind as the API names it", () => {
    expect(
      draftTypeLabel({ kind: "DespatchAdvice", tipo_comprobante: "09" }),
    ).toBe("DespatchAdvice");
  });

  it("tells types and notes apart", () => {
    expect(isDraftTipo("08")).toBe(true);
    expect(isDraftTipo("09")).toBe(false);
    expect(isNoteTipo("07")).toBe(true);
    expect(isNoteTipo("01")).toBe(false);
  });
});
