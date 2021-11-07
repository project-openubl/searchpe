import React, { lazy, Suspense } from "react";
import { Switch, Route } from "react-router-dom";

import { AppPlaceholder } from "shared/components";
import { Paths } from "Paths";

const VersionList = lazy(() => import("./version-list"));

export const Versions: React.FC = () => {
  return (
    <>
      <Suspense fallback={<AppPlaceholder />}>
        <Switch>
          <Route path={Paths.versionList} component={VersionList} exact />
        </Switch>
      </Suspense>
    </>
  );
};
