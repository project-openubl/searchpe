import { describe, expect, it } from "vitest";

import {
  isDataSubjectRequestKind,
  personalDataDocuments,
  toDataSubjectRequestRow,
} from "./data-subject-requests";

describe("data subject requests", () => {
  it("reads a request off the free map", () => {
    expect(
      toDataSubjectRequestRow({
        id: "r1",
        subject_doc_type: "1",
        subject_doc_number: "12345678",
        kind: "access",
        status: "open",
        response_due_at: "2026-02-01T00:00:00Z",
        closed_at: null,
      }),
    ).toMatchObject({
      id: "r1",
      subjectDocType: "1",
      subjectDocNumber: "12345678",
      kind: "access",
      status: "open",
      responseDueAt: "2026-02-01T00:00:00Z",
      closedAt: "",
    });
  });

  it("lists the person's documents and skips malformed entries", () => {
    expect(
      personalDataDocuments({
        request: {},
        documents: [
          {
            id: "d1",
            issuer_ruc: "20123456789",
            tipo_comprobante: "03",
            serie_numero: "B001-1",
            total: "10.00",
            status: "accepted",
          },
          "nope",
        ],
      }),
    ).toEqual([
      expect.objectContaining({
        id: "d1",
        issuerRuc: "20123456789",
        serieNumero: "B001-1",
        total: "10.00",
      }),
    ]);
    expect(personalDataDocuments({})).toEqual([]);
  });

  it("knows the ARCO rights", () => {
    expect(isDataSubjectRequestKind("opposition")).toBe(true);
    expect(isDataSubjectRequestKind("erasure")).toBe(false);
  });
});
