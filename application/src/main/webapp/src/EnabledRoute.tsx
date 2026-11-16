import React from "react";
import { Navigate } from "react-router";

export interface IEnabledRouteProps {
  isEnabled: boolean;
  children: React.ReactNode;
}

export const EnabledRoute: React.FC<IEnabledRouteProps> = ({
  isEnabled,
  children,
}) => {
  return isEnabled ? <>{children}</> : <Navigate to="/" />;
};
