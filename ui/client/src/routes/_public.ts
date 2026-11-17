import { createFileRoute } from "@tanstack/react-router";

// Pages anyone can open without a session (ADR 0003): no login, no token on their requests.
export const Route = createFileRoute("/_public")({});
