import React from "react";
import { Navigate } from "react-router-dom";

export interface IEnabledRouteProps {
  isEnabled: boolean;
  children: any;
}

export const EnabledRoute: React.FC<IEnabledRouteProps> = ({
  isEnabled,
  children,
}) => {
  return isEnabled ? children : <Navigate to="/" />;
};
