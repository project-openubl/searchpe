import { createFileRoute } from "@tanstack/react-router";

import { Home } from "@app/pages/home/Home";

export const Route = createFileRoute("/_authenticated/")({
  component: Home,
});
