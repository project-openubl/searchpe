import { describe, expect, it } from "vitest";

import { toEvents, webhookSchema } from "./webhookForm";

describe("webhook form", () => {
  it("sends `*` alone when every event is picked", () => {
    expect(toEvents(["*", "document.accepted"])).toEqual(["*"]);
    expect(toEvents(["document.accepted"])).toEqual(["document.accepted"]);
  });

  it("asks for an https URL and at least one event", async () => {
    await expect(
      webhookSchema.validate({ url: "http://erp.pe/hook", events: ["*"] }),
    ).rejects.toThrow("La URL debe usar https");
    await expect(
      webhookSchema.validate({ url: "https://erp.pe/hook", events: [] }),
    ).rejects.toThrow("Elige al menos un evento");
  });
});
