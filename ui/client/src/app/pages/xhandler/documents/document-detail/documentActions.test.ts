import { describe, expect, it } from "vitest";

import type { DocumentView } from "@app/client/xhandler";

import { documentActionAvailability } from "./documentActions";

const accepted: DocumentView = {
  id: "d1",
  tipo_comprobante: "01",
  status: "accepted",
  cdr_received_at: "2026-01-01T00:00:00Z",
};

describe("documentActionAvailability", () => {
  it("offers voiding an accepted, undelivered factura", () => {
    const actions = documentActionAvailability({
      document: accepted,
      voidingDocument: undefined,
    });
    expect(actions.void).toBeUndefined();
    expect(actions.retry).toBeDefined();
    expect(actions.cdr).toBeUndefined();
  });

  it("suggests a credit note once the buyer has the document", () => {
    const actions = documentActionAvailability({
      document: { ...accepted, delivered_at: "2026-01-02T00:00:00Z" },
      voidingDocument: undefined,
    });
    expect(actions.void).toMatch(/nota de crédito/);
  });

  it("never voids a guía or a summary", () => {
    for (const tipo of ["09", "31", "RA", "RC", "RR"]) {
      const actions = documentActionAvailability({
        document: { ...accepted, tipo_comprobante: tipo },
        voidingDocument: undefined,
      });
      expect(actions.void).toBeDefined();
    }
  });

  it("offers a resend for an exception, not after a lost ticket", () => {
    const exception = { ...accepted, status: "exception" };
    expect(
      documentActionAvailability({
        document: exception,
        voidingDocument: undefined,
      }).retry,
    ).toBeUndefined();
    expect(
      documentActionAvailability({
        document: { ...exception, code_class: "ticket_lost" },
        voidingDocument: undefined,
      }).retry,
    ).toBeDefined();
  });

  it("cancels a void only when its summary ended in an exception", () => {
    const voiding = { ...accepted, status: "voiding" };
    expect(
      documentActionAvailability({
        document: voiding,
        voidingDocument: { status: "exception" },
      }).cancelVoid,
    ).toBeUndefined();
    expect(
      documentActionAvailability({
        document: voiding,
        voidingDocument: { status: "pending_send" },
      }).cancelVoid,
    ).toBeDefined();
  });

  it("has no files before a draft is signed and no CDR before SUNAT answers", () => {
    const actions = documentActionAvailability({
      document: { id: "d2", status: "draft" },
      voidingDocument: undefined,
    });
    expect(actions.files).toBeDefined();
    expect(actions.cdr).toBeDefined();
  });
});
