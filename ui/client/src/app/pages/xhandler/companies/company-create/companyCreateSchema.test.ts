import { describe, expect, it } from "vitest";

import { companyCreateSchema } from "./companyCreateSchema";

describe("companyCreateSchema", () => {
  it("asks for an 11-digit RUC and the razón social", async () => {
    await expect(
      companyCreateSchema.validate({ ruc: "123", razon_social: "Bodega" }),
    ).rejects.toThrow("El RUC tiene 11 dígitos");
    await expect(
      companyCreateSchema.validate({ ruc: "20123456789", razon_social: "" }),
    ).rejects.toThrow("Ingresa la razón social");
    await expect(
      companyCreateSchema.validate({
        ruc: " 20123456789 ",
        razon_social: "Bodega SAC",
      }),
    ).resolves.toMatchObject({ ruc: "20123456789" });
  });
});
