import type { Status } from "@app/client/xhandler";

/** Job priority classes (DESIGN.md §10.5), as the status endpoint numbers them. */
export const PRIORITY_CLASS_LABELS: Record<number, string> = {
  1: "1 · Reenvío OSE",
  2: "2 · Envíos a SUNAT",
  3: "3 · Webhooks y correos",
  4: "4 · Reportes, SIRE y exportaciones",
};

/** Each component the status reports, with its Spanish name. */
export const componentHealth = (
  status: Status,
): { label: string; isHealthy: boolean | undefined }[] => [
  { label: "Base de datos", isHealthy: status.database },
  { label: "Almacenamiento", isHealthy: status.storage },
  { label: "Bóveda de claves", isHealthy: status.key_vault },
  { label: "Motor", isHealthy: status.engine },
];

/** SUNAT environments as `GET /status` names them (`SunatEnvironment.environment`). */
export const SUNAT_ENVIRONMENT_LABELS: Record<string, string> = {
  production: "Producción",
  beta: "Beta",
  fake: "Pruebas (fake)",
};

/** SUNAT web services (`SunatEndpoint.service`). */
export const SUNAT_SERVICE_LABELS: Record<string, string> = {
  invoice: "Comprobantes",
  despatch: "Guías",
  perception: "Percepciones y retenciones",
};

/** Circuit breaker states (`SunatEndpoint.breaker_state`, DESIGN.md §24.2). */
export const BREAKER_STATE_LABELS: Record<string, string> = {
  closed: "Responde",
  open: "Caído",
  half_open: "Probando",
};

export interface ISunatServiceHealth {
  label: string;
  state: string;
  isUp: boolean;
  paused: boolean;
  until: string | undefined;
}

export interface ISunatEnvironmentHealth {
  environment: string;
  label: string;
  isAvailable: boolean;
  services: ISunatServiceHealth[];
}

/**
 * SUNAT availability per environment, with Spanish names. Unknown codes are shown as returned;
 * an environment nothing was sent to yet has no services and reads as available.
 */
export const sunatHealth = (status: Status): ISunatEnvironmentHealth[] =>
  (status.sunat ?? []).map((env) => ({
    environment: env.environment ?? "",
    label:
      SUNAT_ENVIRONMENT_LABELS[env.environment ?? ""] ?? env.environment ?? "-",
    isAvailable: env.available ?? false,
    services: (env.endpoints ?? []).map((endpoint) => ({
      label:
        SUNAT_SERVICE_LABELS[endpoint.service ?? ""] ?? endpoint.service ?? "-",
      state: endpoint.paused
        ? "Pausado"
        : (BREAKER_STATE_LABELS[endpoint.breaker_state ?? ""] ??
          endpoint.breaker_state ??
          "-"),
      isUp: endpoint.breaker_state === "closed" && !endpoint.paused,
      paused: endpoint.paused ?? false,
      until: endpoint.breaker_until,
    })),
  }));
