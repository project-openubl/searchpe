import type React from "react";

import { getRouteApi } from "@tanstack/react-router";

import { ConformityDetail } from "./ConformityDetail";

const routeApi = getRouteApi(
  "/_authenticated/organizaciones/$tenantId/empresas/$companyId_/conformidad/$documentId",
);

export const ConformityDetailRoute: React.FC = () => {
  const { companyId, documentId } = routeApi.useLoaderData();
  return <ConformityDetail companyId={companyId} documentId={documentId} />;
};
