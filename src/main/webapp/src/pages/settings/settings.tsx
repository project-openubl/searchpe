import React, { lazy, Suspense } from "react";
import { Routes, Route } from "react-router-dom";

import { SimplePlaceholder } from "@project-openubl/lib-ui";

const UserList = lazy(() => import("./user-list"));

export const Settings: React.FC = () => {
  return (
    <>
      <Suspense fallback={<SimplePlaceholder />}>
        <Routes>
          <Route path="/users" element={<UserList />} />
        </Routes>
      </Suspense>
    </>
  );
};
