import { describe, expect, it } from "vitest";

import {
  isDocumentSettling,
  isDocumentStatus,
  isTipoComprobante,
  needsReversalDate,
} from "./documents";

describe("document helpers", () => {
  it("refreshes documents SUNAT may still change", () => {
    for (const status of [
      "pending_send",
      "in_progress",
      "unknown",
      "voiding",
    ]) {
      expect(isDocumentSettling(status)).toBe(true);
    }
    for (const status of ["accepted", "rejected", "exception", "voided"]) {
      expect(isDocumentSettling(status)).toBe(false);
    }
  });

  it("recognizes the API's statuses and types", () => {
    expect(isDocumentStatus("accepted_with_observations")).toBe(true);
    expect(isDocumentStatus("sent")).toBe(false);
    expect(isTipoComprobante("RR")).toBe(true);
    expect(isTipoComprobante("02")).toBe(false);
  });

  it("asks the reversal date only for retenciones and percepciones", () => {
    expect(needsReversalDate({ tipo_comprobante: "20" })).toBe(true);
    expect(needsReversalDate({ tipo_comprobante: "40" })).toBe(true);
    expect(needsReversalDate({ tipo_comprobante: "01" })).toBe(false);
  });
});
