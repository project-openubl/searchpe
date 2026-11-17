import { StrictMode } from "react";
import { createRoot } from "react-dom/client";

import { QueryClientProvider } from "@tanstack/react-query";
import { ReactQueryDevtools } from "@tanstack/react-query-devtools";

import { AnimationsProvider } from "@patternfly/react-core";

import "@app/dayjs";
import { App } from "@app/App";
import { initInterceptors } from "@app/axios-config/apiInit";
import { AuthProvider } from "@app/context/Auth/AuthProvider";
import { queryClient } from "@app/queries/config";
import { syncLastTenantAcrossTabs } from "@app/stores/useLastTenantStore";

import { router } from "./router";

initInterceptors();
syncLastTenantAcrossTabs();

const container = document.getElementById("root");
const root = createRoot(container!);

root.render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <AnimationsProvider config={{ hasAnimations: true }}>
          <App router={router} />
        </AnimationsProvider>
      </AuthProvider>
      {import.meta.env.DEV ? (
        <ReactQueryDevtools initialIsOpen={false} />
      ) : null}
    </QueryClientProvider>
  </StrictMode>,
);
