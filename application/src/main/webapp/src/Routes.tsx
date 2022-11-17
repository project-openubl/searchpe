import { lazy, Suspense } from "react";
import { Navigate, Route, Routes } from "react-router-dom";

import { SimplePlaceholder } from "@project-openubl/lib-ui";

import { RoleRoute } from "./RoleRoute";
import { EnabledRoute } from "./EnabledRoute";
import {
  isAdvancedSearchEnabled,
  isBasicDefaultAuth,
  Permission,
} from "Constants";
import { Paths } from "Paths";

const ConsultaRuc = lazy(() => import("./pages/consulta-ruc"));
const Contribuyentes = lazy(() => import("./pages/contribuyentes"));
const Versions = lazy(() => import("./pages/versions"));
const Users = lazy(() => import("./pages/users"));
const Profile = lazy(() => import("./pages/profile"));

interface IRouteWrapperProps {
  children: any;
}

export const RouteWrapper: React.FC<IRouteWrapperProps> = ({ children }) => {
  if (!isAdvancedSearchEnabled()) {
    return <Navigate to={Paths.consultaRuc} />;
  }

  return children;
};

export const AppRoutes = () => {
  const routes = [
    {
      Component: ConsultaRuc,
      path: Paths.consultaRuc,
      hasDescendant: false,
      hasAny: [Permission.admin, Permission.search],
      enabled: true,
    },
    {
      Component: Contribuyentes,
      path: Paths.contribuyentes,
      hasDescendant: false,
      hasAny: [Permission.admin, Permission.search],
      enabled: isAdvancedSearchEnabled(),
    },
    {
      Component: Versions,
      path: Paths.versiones,
      hasDescendant: false,
      hasAny: [Permission.admin, Permission.version_write],
      enabled: true,
    },
    {
      Component: Users,
      path: Paths.users,
      hasDescendant: false,
      hasAny: [Permission.admin, Permission.user_write],
      enabled: true,
    },
    {
      Component: Profile,
      path: Paths.profile,
      hasDescendant: true,
      hasAny: undefined,
      enabled: isBasicDefaultAuth(),
    },
  ];

  return (
    <Suspense fallback={<SimplePlaceholder />}>
      <Routes>
        {routes.map(
          ({ enabled, path, hasAny, hasDescendant, Component }, index) => (
            <Route
              key={index}
              path={hasDescendant ? `${path}/*` : path}
              element={
                <EnabledRoute isEnabled={enabled}>
                  <RoleRoute hasAny={hasAny}>
                    <Component />
                  </RoleRoute>
                </EnabledRoute>
              }
            />
          )
        )}
        <Route path="*" element={<Navigate to={Paths.consultaRuc} />} />
      </Routes>
    </Suspense>
  );
};
