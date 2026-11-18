import type React from "react";

import { getRouteApi } from "@tanstack/react-router";

import { ReportList } from "./ReportList";

const routeApi = getRouteApi(
  "/_authenticated/organizaciones/$tenantId/empresas/$companyId_/reportes",
);

export const ReportListRoute: React.FC = () => {
  const { companyId } = routeApi.useLoaderData();
  return <ReportList companyId={companyId} />;
};
