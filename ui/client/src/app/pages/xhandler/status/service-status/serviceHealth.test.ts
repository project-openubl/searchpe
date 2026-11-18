import { describe, expect, it } from "vitest";

import { componentHealth, sunatHealth } from "./serviceHealth";

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

describe("sunatHealth", () => {
  it("names each environment and service, and the breaker's state", () => {
    expect(
      sunatHealth({
        sunat: [
          {
            environment: "production",
            available: false,
            endpoints: [
              {
                service: "invoice",
                breaker_state: "open",
                breaker_until: "2026-11-18T10:00:00Z",
                paused: false,
              },
              { service: "despatch", breaker_state: "closed", paused: true },
              { service: "perception", breaker_state: "half_open" },
            ],
          },
          { environment: "beta", available: true, endpoints: [] },
          { environment: "fake", available: true },
        ],
      }),
    ).toEqual([
      {
        environment: "production",
        label: "Producción",
        isAvailable: false,
        services: [
          {
            label: "Comprobantes",
            state: "Caído",
            isUp: false,
            paused: false,
            until: "2026-11-18T10:00:00Z",
          },
          {
            label: "Guías",
            state: "Pausado",
            isUp: false,
            paused: true,
            until: undefined,
          },
          {
            label: "Percepciones y retenciones",
            state: "Probando",
            isUp: false,
            paused: false,
            until: undefined,
          },
        ],
      },
      {
        environment: "beta",
        label: "Beta",
        isAvailable: true,
        services: [],
      },
      {
        environment: "fake",
        label: "Pruebas (fake)",
        isAvailable: true,
        services: [],
      },
    ]);
  });

  it("shows unknown codes as returned and no section without SUNAT data", () => {
    expect(sunatHealth({})).toEqual([]);
    expect(
      sunatHealth({
        sunat: [
          {
            environment: "otro",
            available: true,
            endpoints: [{ service: "nuevo", breaker_state: "raro" }],
          },
        ],
      })[0],
    ).toMatchObject({
      label: "otro",
      services: [{ label: "nuevo", state: "raro", isUp: false }],
    });
  });
});
