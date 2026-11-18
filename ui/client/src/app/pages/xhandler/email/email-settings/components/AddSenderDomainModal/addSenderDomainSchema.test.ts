import { describe, expect, it } from "vitest";

import { addSenderDomainSchema } from "./addSenderDomainSchema";

describe("addSenderDomainSchema", () => {
  it("accepts a domain and lowercases it", async () => {
    await expect(
      addSenderDomainSchema.validate({ domain: " Empresa.com.PE " }),
    ).resolves.toEqual({ domain: "empresa.com.pe" });
  });

  it("refuses addresses and URLs", async () => {
    await expect(
      addSenderDomainSchema.validate({ domain: "ventas@empresa.pe" }),
    ).rejects.toThrow(/sin @/);
    await expect(
      addSenderDomainSchema.validate({ domain: "https://empresa.pe" }),
    ).rejects.toThrow(/sin @/);
  });
});
