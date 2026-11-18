import type React from "react";

import { getRouteApi } from "@tanstack/react-router";

import { ReceptionList } from "./ReceptionList";

const routeApi = getRouteApi(
  "/_authenticated/organizaciones/$tenantId/empresas/$companyId_/recepciones",
);

export const ReceptionListRoute: React.FC = () => {
  const { companyId } = routeApi.useLoaderData();
  return <ReceptionList companyId={companyId} />;
};
