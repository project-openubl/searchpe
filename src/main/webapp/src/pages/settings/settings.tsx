import React, { lazy, Suspense } from "react";
import { Switch, Route, Redirect } from "react-router-dom";

import { AppPlaceholder } from "shared/components";
import { Paths } from "Paths";

const UserList = lazy(() => import("./user-list"));

export const Settings: React.FC = () => {
  return (
    <>
      <Suspense fallback={<AppPlaceholder />}>
        <Switch>
          <Route path={Paths.settings_userList} component={UserList} exact />
          <Redirect from={Paths.settings} to={Paths.settings_userList} exact />
        </Switch>
      </Suspense>
    </>
  );
};
