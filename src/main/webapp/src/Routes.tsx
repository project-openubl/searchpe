import { lazy, Suspense } from "react";
import { Routes, Route, Navigate } from "react-router-dom";

import { SimplePlaceholder } from "@project-openubl/lib-ui";

import { ProtectedRoute } from "ProtectedRoute";
import { isElasticsearchEnabled, Permission } from "Constants";
import { Paths } from "Paths";

const ConsultaRuc = lazy(() => import("./pages/consulta-ruc"));
const Contribuyentes = lazy(() => import("./pages/contribuyentes"));
const Versions = lazy(() => import("./pages/versions"));
const SettingsUsers = lazy(() => import("./pages/settings/users"));

export const AppRoutes = () => {
  const routes = [
    {
      Component: ConsultaRuc,
      path: Paths.consultaRuc,
      hasAny: [Permission.admin, Permission.search],
    },
    {
      Component: Contribuyentes,
      path: Paths.contribuyentes,
      hasAny: [Permission.admin, Permission.search],
    },
    {
      Component: Versions,
      path: Paths.versiones,
      hasAny: [Permission.admin, Permission.version_write],
    },
    {
      Component: SettingsUsers,
      path: Paths.settings_users,
      hasAny: [Permission.admin, Permission.user_write],
    },
  ];

  return (
    <Suspense fallback={<SimplePlaceholder />}>
      <Routes>
        {routes.map(({ path, hasAny, Component }, index) => (
          <Route
            key={index}
            path={path}
            element={
              <ProtectedRoute hasAny={hasAny}>
                <Component />
              </ProtectedRoute>
            }
          />
        ))}
        <Route
          path="*"
          element={
            <Navigate
              to={
                isElasticsearchEnabled()
                  ? Paths.contribuyentes
                  : Paths.consultaRuc
              }
            />
          }
        />
      </Routes>
    </Suspense>
  );
};
