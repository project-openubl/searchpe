import { describe, expect, it } from "vitest";

import {
  publicDocumentStatusLabel,
  publicFileOrder,
} from "./documentPresentation";

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

describe("publicFileOrder", () => {
  it("puts the PDF first, then the XML and the CDR", () => {
    expect(publicFileOrder(["cdr", "xml", "pdf"])).toEqual([
      "pdf",
      "xml",
      "cdr",
    ]);
  });
});
