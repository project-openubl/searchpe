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

  it("fills the fields the spec leaves optional", () => {
    expect(toAdapterTokenRow({ id: "t2" })).toMatchObject({
      id: "t2",
      label: "",
      revokedAt: undefined,
    });
  });
});
