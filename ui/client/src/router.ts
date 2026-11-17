import { createRouter } from "@tanstack/react-router";

import type { IAuthContext } from "@app/context/Auth/AuthContext";
import { queryClient } from "@app/queries/config";
import { parseSearch, stringifySearch } from "@app/utils/searchParams";

import { routeTree } from "./routeTree.gen";

export interface IRouterContext {
  queryClient: typeof queryClient;
  auth: IAuthContext | null;
}

export const router = createRouter({
  routeTree,
  context: {
    queryClient,
    auth: null,
  },
  defaultPreload: "intent",
  // Plain-string URL params (ADR 0008): schemas convert them, arrays repeat the name.
  parseSearch,
  stringifySearch,
});

export type AppRouter = typeof router;

declare module "@tanstack/react-router" {
  interface Register {
    router: AppRouter;
  }
}
