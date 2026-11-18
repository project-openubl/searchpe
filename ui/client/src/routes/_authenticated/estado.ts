import { createFileRoute } from "@tanstack/react-router";

import { ServiceStatus } from "@app/pages/xhandler/status/service-status/ServiceStatus";

export const Route = createFileRoute("/_authenticated/estado")({
  component: ServiceStatus,
});
