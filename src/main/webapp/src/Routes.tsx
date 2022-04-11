import { lazy, Suspense } from "react";
import { Routes, Route, Navigate } from "react-router-dom";

import { SimplePlaceholder } from "@project-openubl/lib-ui";

import { ProtectedRoute } from "ProtectedRoute";
import { isElasticsearchEnabled, Permission } from "Constants";

import { Paths } from "./Paths";

const ConsultaRuc = lazy(() => import("./pages/consulta-ruc"));
const Contribuyentes = lazy(() => import("./pages/contribuyentes"));
const Versions = lazy(() => import("./pages/versions"));
const Settings = lazy(() => import("./pages/settings"));

export const AppRoutes = () => {
  // const routes = [
  //   {
  //     Component: ConsultaRuc,
  //     path: Paths.consultaRuc,
  //     hasAny: [Permission.admin, Permission.search],
  //   },
  //   {
  //     Component: Contribuyentes,
  //     path: Paths.contribuyenteList,
  //     hasAny: [Permission.admin, Permission.search],
  //   },
  //   {
  //     Component: Versions,
  //     path: Paths.versionList,
  //     hasAny: [Permission.admin, Permission.version_write],
  //   },
  //   {
  //     Component: Settings,
  //     path: Paths.settings,
  //     hasAny: [Permission.admin, Permission.user_write],
  //   },
  // ];

  return (
    <Suspense fallback={<SimplePlaceholder />}>
      <Routes>
        <Route
          path={Paths.consultaRuc}
          element={
            <ProtectedRoute hasAny={[Permission.admin, Permission.search]}>
              <ConsultaRuc />
            </ProtectedRoute>
          }
        />
        <Route
          path="/contribuyentes/*"
          element={
            <ProtectedRoute hasAny={[Permission.admin, Permission.search]}>
              <Contribuyentes />
            </ProtectedRoute>
          }
        />
        <Route
          path="/versiones/*"
          element={
            <ProtectedRoute
              hasAny={[Permission.admin, Permission.version_write]}
            >
              <Versions />
            </ProtectedRoute>
          }
        />
        <Route
          path="/settings/*"
          element={
            <ProtectedRoute hasAny={[Permission.admin, Permission.user_write]}>
              <Settings />
            </ProtectedRoute>
          }
        />
        <Route
          path="*"
          element={
            <Navigate
              to={
                isElasticsearchEnabled()
                  ? Paths.contribuyenteList
                  : Paths.consultaRuc
              }
            />
          }
        />
      </Routes>
    </Suspense>
  );
};
