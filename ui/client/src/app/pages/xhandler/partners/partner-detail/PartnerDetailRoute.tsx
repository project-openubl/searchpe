import type React from "react";

import { getRouteApi } from "@tanstack/react-router";

import { PartnerDetail } from "./PartnerDetail";

const routeApi = getRouteApi("/_authenticated/socios/$partnerId");

export const PartnerDetailRoute: React.FC = () => {
  const { partnerId } = routeApi.useLoaderData();
  return <PartnerDetail partnerId={partnerId} />;
};
