import { createFileRoute } from "@tanstack/react-router";

import { SessionClosed } from "@app/pages/public/session-closed/SessionClosed";

export const Route = createFileRoute("/_public/sesion-cerrada")({
  component: SessionClosed,
});
