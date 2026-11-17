import { createFileRoute } from "@tanstack/react-router";

import { ConsultaRuc } from "@app/pages/searchpe/contribuyentes/consulta-ruc/ConsultaRuc";

export const Route = createFileRoute("/_authenticated/padron/consulta")({
  // The searched number stays in the URL, so a result can be shared and reloaded.
  validateSearch: (search: { numero?: unknown }): { numero?: string } =>
    typeof search.numero === "string" && search.numero
      ? { numero: search.numero }
      : {},
  component: ConsultaRuc,
});
