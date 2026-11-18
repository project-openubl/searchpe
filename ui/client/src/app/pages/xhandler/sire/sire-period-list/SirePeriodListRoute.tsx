import type React from "react";

import { getRouteApi } from "@tanstack/react-router";

import { SirePeriodList } from "./SirePeriodList";

const routeApi = getRouteApi(
  "/_authenticated/organizaciones/$tenantId/empresas/$companyId_/sire_/periodos",
);

export const SirePeriodListRoute: React.FC = () => {
  const { companyId } = routeApi.useLoaderData();
  return <SirePeriodList companyId={companyId} />;
};
