import { createFileRoute, redirect } from "@tanstack/react-router";

// A tenant's landing page is its companies (ADR 0002).
export const Route = createFileRoute(
  "/_authenticated/organizaciones/$tenantId/",
)({
  beforeLoad: ({ params }) => {
    throw redirect({
      to: "/organizaciones/$tenantId/empresas",
      params,
      replace: true,
    });
  },
});
