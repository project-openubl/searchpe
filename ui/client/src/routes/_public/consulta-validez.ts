import { createFileRoute } from "@tanstack/react-router";

import { ConsultaValidez } from "@app/pages/public/consulta-validez/ConsultaValidez";

export const Route = createFileRoute("/_public/consulta-validez")({
  component: ConsultaValidez,
});
