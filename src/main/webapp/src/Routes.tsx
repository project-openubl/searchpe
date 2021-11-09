import { isElasticsearchEnabled } from "Constants";
import React, { lazy, Suspense } from "react";
import { Route, Switch, Redirect } from "react-router-dom";

import { AppPlaceholder } from "shared/components";
import { Paths } from "./Paths";

const ConsultaRuc = lazy(() => import("./pages/consulta-ruc"));
const Contribuyentes = lazy(() => import("./pages/contribuyentes"));
const Versions = lazy(() => import("./pages/versions"));
const Settings = lazy(() => import("./pages/settings"));

export const AppRoutes = () => {
  const routes = [
    { component: ConsultaRuc, path: Paths.consultaRuc, exact: false },
    { component: Contribuyentes, path: Paths.contribuyenteList, exact: false },
    { component: Versions, path: Paths.versionList, exact: false },
    { component: Settings, path: Paths.settings, exact: false },
  ];

  return (
    <Suspense fallback={<AppPlaceholder />}>
      <Switch>
        {routes.map(({ path, component, ...rest }, index) => (
          <Route key={index} path={path} component={component} {...rest} />
        ))}
        <Redirect
          from={Paths.base}
          to={
            isElasticsearchEnabled()
              ? Paths.contribuyenteList
              : Paths.consultaRuc
          }
          exact
        />
      </Switch>
    </Suspense>
  );
};