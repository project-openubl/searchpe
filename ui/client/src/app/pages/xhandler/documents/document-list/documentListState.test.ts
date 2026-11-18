import { describe, expect, it } from "vitest";

import { DOCUMENT_LIST_STATE } from "./documentListState";

describe("DOCUMENT_LIST_STATE", () => {
  it("keeps the buyer filters, empty by default", () => {
    const { schema, defaultValue } = DOCUMENT_LIST_STATE.filters;
    expect(defaultValue).toMatchObject({ comprador: "", nombreComprador: "" });
    expect(
      schema.parse({
        ...defaultValue,
        comprador: "20123456789",
        nombreComprador: "Bodega",
      }),
    ).toMatchObject({ comprador: "20123456789", nombreComprador: "Bodega" });
  });
});
