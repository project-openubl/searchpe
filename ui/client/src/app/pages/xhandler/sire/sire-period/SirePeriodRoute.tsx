import type React from "react";

import { getRouteApi } from "@tanstack/react-router";

import { SirePeriod } from "./SirePeriod";

const routeApi = getRouteApi(
  "/_authenticated/organizaciones/$tenantId/empresas/$companyId_/sire",
);

export const SirePeriodRoute: React.FC = () => {
  const { companyId } = routeApi.useLoaderData();
  return <SirePeriod companyId={companyId} />;
};
