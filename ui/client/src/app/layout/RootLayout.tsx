import type React from "react";

import { Outlet, useRouterState } from "@tanstack/react-router";

import { Bullseye } from "@patternfly/react-core";
import ExclamationCircleIcon from "@patternfly/react-icons/dist/esm/icons/exclamation-circle-icon";

import { AppPlaceholder } from "@app/components/AppPlaceholder";
import { FullPageEmptyState } from "@app/components/FullPageEmptyState";
import { ApiStatusProvider } from "@app/context/ApiStatus/ApiStatusProvider";
import { NotificationsProvider } from "@app/context/Notifications/NotificationsProvider";
import { useAuth } from "@app/hooks/useAuth";

import { DefaultLayout } from "./DefaultLayout";
import { PublicLayout } from "./PublicLayout";

export const RootComponent: React.FC = () => {
  const auth = useAuth();
  // Public routes (ADR 0003) get a bare shell: no navigation, no session, no status polling.
  const isPublicRoute = useRouterState({
    select: (state) =>
      state.matches.some((match) => match.routeId.startsWith("/_public")),
  });
  const isPrivateRoute = useRouterState({
    select: (state) =>
      state.matches.some((match) =>
        match.routeId.startsWith("/_authenticated"),
      ),
  });
  // A private page without a session is on its way to Keycloak: no shell, no queries.
  const isLeavingToLogin =
    isPrivateRoute && !auth.isAuthenticated() && !auth.error;

  return (
    <NotificationsProvider>
      {isPublicRoute ? (
        <PublicLayout>
          <Outlet />
        </PublicLayout>
      ) : isLeavingToLogin ? (
        <AppPlaceholder />
      ) : auth.error ? (
        // Keycloak unreachable or misconfigured: private pages can't start a session.
        <Bullseye>
          <FullPageEmptyState
            titleText="No se pudo iniciar sesión"
            icon={ExclamationCircleIcon}
            status="danger"
            body={`El servicio de inicio de sesión no responde (${auth.error.message}). Inténtalo de nuevo en unos minutos o contacta a tu administrador.`}
          />
        </Bullseye>
      ) : (
        <ApiStatusProvider>
          <DefaultLayout>
            <Outlet />
          </DefaultLayout>
        </ApiStatusProvider>
      )}
    </NotificationsProvider>
  );
};
