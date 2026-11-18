import type React from "react";

import { getRouteApi } from "@tanstack/react-router";

import { ConformityList } from "./ConformityList";

const routeApi = getRouteApi(
  "/_authenticated/organizaciones/$tenantId/empresas/$companyId_/conformidad/",
);

export const ConformityListRoute: React.FC = () => {
  const { companyId } = routeApi.useLoaderData();
  return <ConformityList companyId={companyId} />;
};
