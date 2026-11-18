import type React from "react";

import { getRouteApi } from "@tanstack/react-router";

import { AdapterTokenList } from "./AdapterTokenList";

const routeApi = getRouteApi(
  "/_authenticated/organizaciones/$tenantId/empresas/$companyId_/adaptadores",
);

export const AdapterTokenListRoute: React.FC = () => {
  const { companyId } = routeApi.useLoaderData();
  return <AdapterTokenList companyId={companyId} />;
};
