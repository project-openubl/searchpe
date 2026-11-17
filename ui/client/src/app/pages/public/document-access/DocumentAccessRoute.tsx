import type React from "react";

import { getRouteApi } from "@tanstack/react-router";

import { DocumentAccess } from "./DocumentAccess";

const routeApi = getRouteApi("/_public/p/documentos/$token");

export const DocumentAccessRoute: React.FC = () => {
  const { token } = routeApi.useParams();
  return <DocumentAccess token={token} />;
};
