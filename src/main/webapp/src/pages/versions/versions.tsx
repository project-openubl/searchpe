import React, { lazy, Suspense } from "react";
import { Routes, Route } from "react-router-dom";

import { SimplePlaceholder } from "@project-openubl/lib-ui";

const VersionList = lazy(() => import("./version-list"));

export const Versions: React.FC = () => {
  return (
    <>
      <Suspense fallback={<SimplePlaceholder />}>
        <Routes>
          <Route path="/" element={<VersionList />} />
        </Routes>
      </Suspense>
    </>
  );
};
