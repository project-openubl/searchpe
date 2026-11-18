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
