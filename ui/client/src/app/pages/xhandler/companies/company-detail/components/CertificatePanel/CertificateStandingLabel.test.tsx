import { describe, expect, it } from "vitest";
import { render } from "vitest-browser-react";

import { CertificateStandingLabel } from "./CertificateStandingLabel";

describe("CertificateStandingLabel", () => {
  it("counts down the days of an expiring certificate", async () => {
    const screen = await render(
      <CertificateStandingLabel standing="expiring" daysLeft={7} />,
    );
    await expect.element(screen.getByText("Vence en 7 días")).toBeVisible();
  });

  it("tells a valid certificate from an expired one", async () => {
    const valid = await render(
      <CertificateStandingLabel standing="valid" daysLeft={200} />,
    );
    await expect.element(valid.getByText("Vigente")).toBeVisible();
    const expired = await render(
      <CertificateStandingLabel standing="expired" daysLeft={-3} />,
    );
    await expect.element(expired.getByText("Vencido")).toBeVisible();
  });
});
