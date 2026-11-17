import { createFileRoute, notFound } from "@tanstack/react-router";

import { ENV } from "@app/env";

// searchpe is optional (ADR 0001): without it its pages don't exist.
export const Route = createFileRoute("/_authenticated/padron")({
  beforeLoad: () => {
    if (ENV.SEARCHPE_ENABLED !== "true") {
      throw notFound();
    }
  },
});
