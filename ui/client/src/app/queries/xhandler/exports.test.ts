import { describe, expect, it } from "vitest";

import { exportParts } from "./exports";

describe("exportParts", () => {
  it("indexes the parts in the order the service lists them", () => {
    expect(
      exportParts({
        parts: [
          { name: "indice.zip", ref: "k1", size: 100, documents: 3 },
          { name: "documentos-0001.zip", ref: "k2", size: 2048 },
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

  it("names a part without a name by its position", () => {
    expect(exportParts({ parts: [{}] })[0].name).toBe("parte-1.zip");
    expect(exportParts({})).toEqual([]);
  });
});
