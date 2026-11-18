import { describe, expect, it } from "vitest";

import {
  isDataSubjectRequestKind,
  personalDataDocuments,
  personalDataDrafts,
  toDataSubjectRequestRow,
} from "./data-subject-requests";

describe("data subject requests", () => {
  it("fills a request's missing fields with empty text", () => {
    expect(
      toDataSubjectRequestRow({
        id: "r1",
        subject_doc_type: "1",
        subject_doc_number: "12345678",
        kind: "access",
        status: "open",
        response_due_at: "2026-02-01T00:00:00Z",
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

  it("lists the drafts naming the person, deleted ones too", () => {
    expect(
      personalDataDrafts({
        drafts: [
          {
            id: "dr1",
            issuer_ruc: "20123456789",
            kind: "Invoice",
            tipo_comprobante: "03",
            serie: "B001",
            nombre: "PERSONA NATURAL",
            status: "draft",
            created_at: "2026-03-01T10:00:00Z",
            deleted_at: "2026-03-02T10:00:00Z",
          },
          null,
        ],
      }),
    ).toEqual([
      {
        id: "dr1",
        issuerRuc: "20123456789",
        tipoComprobante: "03",
        serie: "B001",
        nombre: "PERSONA NATURAL",
        status: "draft",
        createdAt: "2026-03-01T10:00:00Z",
        deletedAt: "2026-03-02T10:00:00Z",
      },
    ]);
    expect(personalDataDrafts({ documents: [] })).toEqual([]);
  });

  it("knows the ARCO rights", () => {
    expect(isDataSubjectRequestKind("opposition")).toBe(true);
    expect(isDataSubjectRequestKind("erasure")).toBe(false);
  });
});
