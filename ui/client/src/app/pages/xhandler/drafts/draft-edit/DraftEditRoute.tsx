import type React from "react";

import { getRouteApi } from "@tanstack/react-router";

import { DraftEdit } from "./DraftEdit";

const routeApi = getRouteApi(
  "/_authenticated/organizaciones/$tenantId/borradores/$draftId",
);

export const DraftEditRoute: React.FC = () => {
  const { draftId } = routeApi.useLoaderData();
  return <DraftEdit draftId={draftId} />;
};
