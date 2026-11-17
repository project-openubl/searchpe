import { createRootRouteWithContext } from "@tanstack/react-router";

import "@patternfly/react-core/dist/styles/base.css";

import { RouteErrorFallback } from "@app/components/RouteErrorFallback";
import { RootComponent } from "@app/layout/RootLayout";
import { NotFound } from "@app/pages/not-found/NotFound";

import type { IRouterContext } from "../router";

export const Route = createRootRouteWithContext<IRouterContext>()({
  component: RootComponent,
  notFoundComponent: NotFound,
  errorComponent: RouteErrorFallback,
});
