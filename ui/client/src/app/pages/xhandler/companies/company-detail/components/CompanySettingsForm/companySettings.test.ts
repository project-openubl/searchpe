import { describe, expect, it } from "vitest";

import { settingsChanges, settingsDefaults } from "./companySettings";

describe("company settings", () => {
  it("starts from the company, with the email texts empty", () => {
    const values = settingsDefaults({
      nombre_comercial: "Bodega",
      pdf_format: "ticket80",
      email_on_accept: true,
    });
    expect(values).toMatchObject({
      nombre_comercial: "Bodega",
      pdf_format: "ticket80",
      email_on_accept: true,
      email_subject: "",
      email_body: "",
    });
    expect(settingsDefaults({ pdf_format: "letter" }).pdf_format).toBe("a4");
  });

  it("sends only the changed fields", () => {
    const values = {
      ...settingsDefaults({}),
      email: "ventas@bodega.pe",
      email_subject: "",
    };
    expect(
      settingsChanges({ values, dirty: { email: true, email_subject: true } }),
    ).toEqual({ email: "ventas@bodega.pe", email_subject: "" });
    expect(settingsChanges({ values, dirty: {} })).toEqual({});
  });
});
