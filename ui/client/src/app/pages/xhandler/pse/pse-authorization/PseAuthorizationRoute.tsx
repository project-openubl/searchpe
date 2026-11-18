import type React from "react";

import { getRouteApi } from "@tanstack/react-router";

import { PseAuthorization } from "./PseAuthorization";

const routeApi = getRouteApi(
  "/_authenticated/organizaciones/$tenantId/empresas/$companyId_/pse",
);

export const PseAuthorizationRoute: React.FC = () => {
  const { companyId } = routeApi.useLoaderData();
  return <PseAuthorization companyId={companyId} />;
};
