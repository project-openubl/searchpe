import { describe, expect, it } from "vitest";

import { emailDocumentSchema, splitAddresses } from "./emailDocumentSchema";

describe("splitAddresses", () => {
  it("splits on commas, semicolons and spaces", () => {
    expect(splitAddresses("a@x.pe, b@x.pe;c@x.pe  d@x.pe")).toEqual([
      "a@x.pe",
      "b@x.pe",
      "c@x.pe",
      "d@x.pe",
    ]);
  });
});

describe("emailDocumentSchema", () => {
  it("needs at least one valid recipient", async () => {
    await expect(
      emailDocumentSchema.validate({ to: "", cc: "", includeCdr: false }),
    ).rejects.toThrow("Escribe al menos un destinatario.");
    await expect(
      emailDocumentSchema.validate({ to: "nope", cc: "", includeCdr: false }),
    ).rejects.toThrow(/correos válidos/);
  });

  it("accepts valid lists and an empty cc", async () => {
    await expect(
      emailDocumentSchema.validate({
        to: "compras@cliente.pe",
        cc: "",
        includeCdr: true,
      }),
    ).resolves.toBeTruthy();
  });

  it("refuses more than ten addresses", async () => {
    const eleven = Array.from({ length: 11 }, (_, i) => `u${i}@x.pe`).join(",");
    await expect(
      emailDocumentSchema.validate({ to: eleven, cc: "", includeCdr: false }),
    ).rejects.toThrow("Hasta 10 correos.");
  });
});
