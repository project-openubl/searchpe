import React, { useEffect } from "react";
import { HashRouter } from "react-router-dom";

import { useDispatch } from "react-redux";
import { fetchCurrentUser } from "store/currentUser/actions";

import { AppRoutes } from "./Routes";
import "./App.scss";

import { DefaultLayout } from "./layout";

import NotificationsPortal from "@redhat-cloud-services/frontend-components-notifications/NotificationPortal";
import "@redhat-cloud-services/frontend-components-notifications/index.css";

import DeleteDialog from "./shared/containers/delete-dialog";

const App: React.FC = () => {
  const dispatch = useDispatch();
  useEffect(() => {
    dispatch(fetchCurrentUser());
  }, [dispatch]);

  return (
    <HashRouter>
      <DefaultLayout>
        <AppRoutes />
      </DefaultLayout>
      <NotificationsPortal />
      <DeleteDialog />
    </HashRouter>
  );
};

export default App;
