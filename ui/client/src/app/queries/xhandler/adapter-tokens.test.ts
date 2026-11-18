import { describe, expect, it } from "vitest";

import { toAdapterTokenRow } from "./adapter-tokens";

describe("toAdapterTokenRow", () => {
  it("reads the fields the service writes", () => {
    expect(
      toAdapterTokenRow({
        id: "t1",
        kind: "nubefact",
        label: "ERP",
        created_by: "alice",
        created_at: "2026-01-01T00:00:00Z",
        revoked_at: null,
      }),
    ).toEqual({
      id: "t1",
      kind: "nubefact",
      label: "ERP",
      createdBy: "alice",
      createdAt: "2026-01-01T00:00:00Z",
      revokedAt: undefined,
    });
  });

  it("ignores values of an unexpected type", () => {
    expect(toAdapterTokenRow({ id: 5, label: {} })).toMatchObject({
      id: "",
      label: "",
    });
  });
});
