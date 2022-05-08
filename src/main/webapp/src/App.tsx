import React from "react";
import { HashRouter } from "react-router-dom";

import { SimplePlaceholder, ConditionalRender } from "@project-openubl/lib-ui";

import {
  Bullseye,
  Button,
  EmptyState,
  EmptyStateBody,
  EmptyStateIcon,
  EmptyStateVariant,
  Title,
} from "@patternfly/react-core";
import { WarningTriangleIcon } from "@patternfly/react-icons";

import { AppRoutes } from "./Routes";
import "./App.scss";

import { DefaultLayout } from "./shared/components/layout";

import NotificationsPortal from "@redhat-cloud-services/frontend-components-notifications/NotificationPortal";
import "@redhat-cloud-services/frontend-components-notifications/index.css";

import { useWhoAmIQuery } from "queries/whoami";

import { ConfirmationContextProvider } from "@project-openubl/lib-ui";

const App: React.FC = () => {
  const whoAmI = useWhoAmIQuery();

  if (whoAmI.isError) {
    return (
      <Bullseye>
        <EmptyState variant={EmptyStateVariant.small}>
          <EmptyStateIcon icon={WarningTriangleIcon} />
          <Title headingLevel="h2" size="lg">
            404 Forbidden
          </Title>
          <EmptyStateBody>
            No se pudo identificar sus credenciales; haga click en 'Login' y una
            vez hecho login refresque la presente página.
          </EmptyStateBody>
          <Button
            variant="primary"
            onClick={() => {
              const loginPopUp = window.open(
                "http://localhost:8080",
                "example",
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
        <NotificationsPortal />
      </ConditionalRender>
    </HashRouter>
  );
};

export default App;
