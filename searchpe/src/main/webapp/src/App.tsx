import React from "react";
import { HashRouter } from "react-router";

import {
  Bullseye,
  Button,
  EmptyState,
  EmptyStateBody,
  EmptyStateVariant,
  EmptyStateFooter,
} from "@patternfly/react-core";
import { WarningTriangleIcon } from "@patternfly/react-icons";

import { AppRoutes } from "./Routes";

import { DefaultLayout } from "./shared/components/layout";

import { useWhoAmIQuery } from "queries/whoami";

import { ConditionalRender, SimplePlaceholder } from "shared/components";
import { ConfirmationContextProvider } from "shared/context";
import { getLoginUrl } from "utils/modelUtils";

const App: React.FC = () => {
  const whoAmI = useWhoAmIQuery();

  if (whoAmI.isError) {
    return (
      <Bullseye>
        <EmptyState
          headingLevel="h2"
          icon={WarningTriangleIcon}
          titleText="403 Forbidden"
          variant={EmptyStateVariant.sm}
        >
          <EmptyStateBody>
            No se pudo identificar sus credenciales; haga click en 'Login' y una
            vez hecho login refresque la presente página.
          </EmptyStateBody>
          <EmptyStateFooter>
            <Button
              variant="primary"
              onClick={() => {
                const loginPopUp = window.open(
                  getLoginUrl(),
                  "searchpe-login",
                  "width=800,height=750"
                );
                const timer = setInterval(function () {
                  if (loginPopUp && loginPopUp.closed) {
                    clearInterval(timer);
                    window.location.replace("/");
                  }
                }, 1000);
              }}
            >
              Login
            </Button>
          </EmptyStateFooter>
        </EmptyState>
      </Bullseye>
    );
  }

  return (
    <HashRouter>
      <ConditionalRender when={whoAmI.isLoading} then={<SimplePlaceholder />}>
        <ConfirmationContextProvider>
          <DefaultLayout>
            <AppRoutes />
          </DefaultLayout>
        </ConfirmationContextProvider>
      </ConditionalRender>
    </HashRouter>
  );
};

export default App;
