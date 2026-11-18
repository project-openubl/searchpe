import { describe, expect, it } from "vitest";

import { routeDefaults, routeSchema, toRouteChange } from "./routeForm";

describe("route form", () => {
  it("starts from the stored route, without the OSE credentials", () => {
    expect(
      routeDefaults({ submission_route: "ose", ose_name: "Mi OSE" }),
    ).toMatchObject({
      submission_route: "ose",
      name: "Mi OSE",
      username: "",
      password: "",
    });
    expect(routeDefaults(undefined).submission_route).toBe("sunat");
  });

  it("sends the OSE only for the OSE route", () => {
    const values = {
      ...routeDefaults(undefined),
      name: "Mi OSE",
      invoice_url: "https://ose.pe/invoice",
      username: "u",
      password: "p",
    };
    expect(toRouteChange(values)).toEqual({ submission_route: "sunat" });
    expect(toRouteChange({ ...values, submission_route: "ose" })).toEqual({
      submission_route: "ose",
      ose: {
        name: "Mi OSE",
        invoice_url: "https://ose.pe/invoice",
        perception_url: undefined,
        username: "u",
        password: "p",
      },
    });
  });

  it("requires the OSE's data only when routing through one", async () => {
    await expect(
      routeSchema.validate(routeDefaults(undefined)),
    ).resolves.toBeTruthy();
    await expect(
      routeSchema.validate({
        ...routeDefaults(undefined),
        submission_route: "ose",
      }),
    ).rejects.toThrow("Ingresa");
  });
});
