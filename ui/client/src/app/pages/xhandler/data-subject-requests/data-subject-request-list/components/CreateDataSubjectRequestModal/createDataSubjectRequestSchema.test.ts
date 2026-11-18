import { describe, expect, it } from "vitest";

import { createDataSubjectRequestSchema } from "./createDataSubjectRequestSchema";

const valid = {
  subjectDocType: "1",
  subjectDocNumber: "12345678",
  subjectName: "Juana Pérez",
  kind: "access",
  details: "",
};

describe("createDataSubjectRequestSchema", () => {
  it("accepts a DNI holder's access request", async () => {
    await expect(
      createDataSubjectRequestSchema.validate(valid),
    ).resolves.toBeTruthy();
  });

  it("checks DNI and RUC lengths", async () => {
    await expect(
      createDataSubjectRequestSchema.validate({
        ...valid,
        subjectDocNumber: "123",
      }),
    ).rejects.toThrow("El DNI tiene 8 dígitos.");
    await expect(
      createDataSubjectRequestSchema.validate({
        ...valid,
        subjectDocType: "6",
        subjectDocNumber: "12345678",
      }),
    ).rejects.toThrow("El RUC tiene 11 dígitos.");
  });

  it("refuses a right the API doesn't know", async () => {
    await expect(
      createDataSubjectRequestSchema.validate({ ...valid, kind: "deletion" }),
    ).rejects.toThrow("Elige el derecho que se ejerce.");
  });
});
