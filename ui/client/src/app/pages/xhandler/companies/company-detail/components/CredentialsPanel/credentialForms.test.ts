import { describe, expect, it } from "vitest";

import {
  credentialSchema,
  EMPTY_CREDENTIAL_VALUES,
  toCredentialWrite,
} from "./credentialForms";

describe("credential forms", () => {
  it("starts empty: a stored secret is never prefilled", () => {
    expect(EMPTY_CREDENTIAL_VALUES.password).toBe("");
    expect(EMPTY_CREDENTIAL_VALUES.client_secret).toBe("");
    expect(EMPTY_CREDENTIAL_VALUES.token).toBe("");
  });

  it("sends each kind only its own fields", () => {
    const values = {
      ...EMPTY_CREDENTIAL_VALUES,
      username: "20123456789MODDATOS",
      password: "moddatos",
      client_id: "id",
    };
    expect(toCredentialWrite({ kind: "sol", values })).toEqual({
      kind: "sol",
      body: { username: "20123456789MODDATOS", password: "moddatos" },
    });
    expect(
      toCredentialWrite({
        kind: "smtp",
        values: { ...values, host: "smtp.example.com", from: "a@b.pe" },
      }),
    ).toEqual({
      kind: "smtp",
      body: {
        host: "smtp.example.com",
        port: 587,
        username: "20123456789MODDATOS",
        password: "moddatos",
        from: "a@b.pe",
        start_tls: true,
      },
    });
    expect(
      toCredentialWrite({
        kind: "lookup",
        values: { ...values, url: "https://x.pe/{tipo}/{numero}" },
      }),
    ).toEqual({
      kind: "lookup",
      body: { url: "https://x.pe/{tipo}/{numero}", token: undefined },
    });
  });

  it("requires what the kind's endpoint requires", async () => {
    await expect(
      credentialSchema("gre").validate(EMPTY_CREDENTIAL_VALUES),
    ).rejects.toThrow();
    await expect(
      credentialSchema("lookup").validate({
        ...EMPTY_CREDENTIAL_VALUES,
        url: "https://x.pe/{tipo}/{numero}",
      }),
    ).resolves.toBeTruthy();
  });
});
