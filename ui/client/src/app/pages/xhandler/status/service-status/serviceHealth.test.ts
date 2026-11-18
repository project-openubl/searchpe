import { describe, expect, it } from "vitest";

import { componentHealth } from "./serviceHealth";

describe("componentHealth", () => {
  it("names each component the status reports", () => {
    expect(
      componentHealth({
        database: true,
        storage: false,
        key_vault: true,
        engine: undefined,
      }),
    ).toEqual([
      { label: "Base de datos", isHealthy: true },
      { label: "Almacenamiento", isHealthy: false },
      { label: "Bóveda de claves", isHealthy: true },
      { label: "Motor", isHealthy: undefined },
    ]);
  });
});
