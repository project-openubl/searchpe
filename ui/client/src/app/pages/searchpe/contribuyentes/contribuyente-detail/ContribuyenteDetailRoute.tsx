import type React from "react";

import { getRouteApi } from "@tanstack/react-router";

import { ContribuyenteDetail } from "./ContribuyenteDetail";

const routeApi = getRouteApi(
  "/_authenticated/padron/contribuyentes/$numeroDocumento",
);

export const ContribuyenteDetailRoute: React.FC = () => {
  const { numeroDocumento } = routeApi.useLoaderData();
  return <ContribuyenteDetail numeroDocumento={numeroDocumento} />;
};
