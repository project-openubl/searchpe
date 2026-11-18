import type React from "react";

import { getRouteApi } from "@tanstack/react-router";

import { DocumentDetail } from "./DocumentDetail";

const routeApi = getRouteApi(
  "/_authenticated/organizaciones/$tenantId/documentos/$documentId",
);

export const DocumentDetailRoute: React.FC = () => {
  const { documentId } = routeApi.useLoaderData();
  return <DocumentDetail documentId={documentId} />;
};
