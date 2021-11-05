import React, { lazy, Suspense } from "react";
import { Switch, Route } from "react-router-dom";

import { AppPlaceholder } from "shared/components";
import { Paths } from "Paths";

const ContribuyenteList = lazy(() => import("./contribuyente-list"));

export const Contribuyentes: React.FC = () => {
  return (
    <>
      <Suspense fallback={<AppPlaceholder />}>
        <Switch>
          <Route
            path={Paths.contribuyenteList}
            component={ContribuyenteList}
            exact
          />
        </Switch>
      </Suspense>
    </>
  );
};
