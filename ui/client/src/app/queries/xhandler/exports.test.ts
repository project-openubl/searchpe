import { describe, expect, it } from "vitest";

import { exportParts } from "./exports";

describe("exportParts", () => {
  it("indexes the parts in the order the service lists them", () => {
    expect(
      exportParts({
        parts: [
          { name: "indice.zip", size: 100, documents: 3 },
          { name: "documentos-0001.zip", size: 2048 },
        ],
      }),
    ).toEqual([
      { index: 0, name: "indice.zip", size: 100, documents: 3 },
      {
        index: 1,
        name: "documentos-0001.zip",
        size: 2048,
        documents: undefined,
      },
    ]);
  });

  it("has no parts before the export is done", () => {
    expect(exportParts({})).toEqual([]);
  });
});
