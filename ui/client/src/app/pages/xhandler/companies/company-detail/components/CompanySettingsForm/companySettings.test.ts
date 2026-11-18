import { describe, expect, it } from "vitest";

import {
  companySettingsSchema,
  settingsChanges,
  settingsDefaults,
} from "./companySettings";

describe("company settings", () => {
  it("starts from the company, its email texts included", () => {
    const values = settingsDefaults({
      nombre_comercial: "Bodega",
      pdf_format: "ticket80",
      email_on_accept: true,
      email_subject: "Su comprobante",
      email_body: "Gracias por su compra.",
    });
    expect(values).toMatchObject({
      nombre_comercial: "Bodega",
      pdf_format: "ticket80",
      email_on_accept: true,
      email_subject: "Su comprobante",
      email_body: "Gracias por su compra.",
    });
    expect(settingsDefaults({})).toMatchObject({
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

  it("starts from the company's Libro de Reclamaciones", () => {
    expect(
      settingsDefaults({
        libro_reclamaciones_url: "https://bodega.pe/reclamos",
      }).libro_reclamaciones_url,
    ).toBe("https://bodega.pe/reclamos");
    expect(settingsDefaults({}).libro_reclamaciones_url).toBe("");
  });

  it("sends the Libro de Reclamaciones when changed, blank to remove it", () => {
    const values = {
      ...settingsDefaults({}),
      libro_reclamaciones_url: "",
    };
    expect(
      settingsChanges({ values, dirty: { libro_reclamaciones_url: true } }),
    ).toEqual({ libro_reclamaciones_url: "" });
    expect(settingsChanges({ values, dirty: {} })).toEqual({});
  });

  it("accepts only an https Libro de Reclamaciones, or none", () => {
    const valid = (url: string) =>
      companySettingsSchema.isValidSync({
        ...settingsDefaults({}),
        libro_reclamaciones_url: url,
      });
    expect(valid("")).toBe(true);
    expect(valid("https://bodega.pe/reclamos")).toBe(true);
    expect(valid("http://bodega.pe/reclamos")).toBe(false);
    expect(valid("bodega.pe/reclamos")).toBe(false);
  });
});
