import type React from "react";

import { getRouteApi } from "@tanstack/react-router";

import { CompanyDetail } from "./CompanyDetail";

const routeApi = getRouteApi(
  "/_authenticated/organizaciones/$tenantId/empresas/$companyId",
);

export const CompanyDetailRoute: React.FC = () => {
  const { companyId } = routeApi.useLoaderData();
  return <CompanyDetail companyId={companyId} />;
};
