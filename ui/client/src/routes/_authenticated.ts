import { createFileRoute } from "@tanstack/react-router";

import { requireAuthentication } from "@app/context/Auth/requireAuthentication";

// Every page under this layout needs a session; public pages live under `_public` (ADR 0003).
export const Route = createFileRoute("/_authenticated")({
  beforeLoad: requireAuthentication,
});
