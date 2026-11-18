import { describe, expect, it } from "vitest";

import { LOGO_MAX_BYTES, logoFileProblem } from "./logoFile";

describe("logoFileProblem", () => {
  it("accepts a small PNG or JPEG", () => {
    expect(
      logoFileProblem(new File(["x"], "logo.png", { type: "image/png" })),
    ).toBeUndefined();
    expect(
      logoFileProblem(new File(["x"], "logo.jpg", { type: "image/jpeg" })),
    ).toBeUndefined();
  });

  it("refuses other types and files over 200 KB", () => {
    expect(
      logoFileProblem(new File(["x"], "logo.gif", { type: "image/gif" })),
    ).toBe("El logo debe ser una imagen PNG o JPEG");
    expect(
      logoFileProblem(
        new File([new Uint8Array(LOGO_MAX_BYTES + 1)], "logo.png", {
          type: "image/png",
        }),
      ),
    ).toBe("El logo no debe pasar de 200 KB");
    expect(logoFileProblem(undefined)).toBe("Elige una imagen");
  });
});
