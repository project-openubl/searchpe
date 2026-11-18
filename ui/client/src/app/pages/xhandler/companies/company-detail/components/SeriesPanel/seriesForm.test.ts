import { describe, expect, it } from "vitest";

import { seriesSchema, tipoComprobanteLabel } from "./seriesForm";

describe("series form", () => {
  it("names the document types and shows unknown codes as returned", () => {
    expect(tipoComprobanteLabel("01")).toBe("Factura");
    expect(tipoComprobanteLabel("99")).toBe("99");
  });

  it("validates SUNAT's 4-character series", async () => {
    await expect(
      seriesSchema.validate({ tipo_comprobante: "01", serie: "F002" }),
    ).resolves.toMatchObject({ serie: "F002" });
    await expect(
      seriesSchema.validate({ tipo_comprobante: "01", serie: "f2" }),
    ).rejects.toThrow("La serie tiene 4 caracteres");
  });

  it("leaves an empty next number to the server", async () => {
    const valid = await seriesSchema.validate({
      tipo_comprobante: "01",
      serie: "F002",
      next_number: "",
    });
    expect(valid.next_number).toBeUndefined();
    await expect(
      seriesSchema.validate({
        tipo_comprobante: "01",
        serie: "F002",
        next_number: "0",
      }),
    ).rejects.toThrow("El número empieza en 1");
  });
});
