import React from "react";
import Keycloak from "keycloak-js";
import { ReactKeycloakProvider } from "@react-keycloak/web";

// Setup Keycloak instance as needed
// Pass initialization options as required or leave blank to load from 'keycloak.json'
const keycloak = Keycloak(
  process.env.PUBLIC_URL + "/api/templates/keycloak.json"
);

const KeycloakWrapper: React.FC = ({ children }) => {
  return (
    <ReactKeycloakProvider
      authClient={keycloak}
      initOptions={{ onLoad: "login-required" }}
      LoadingComponent={<span>Loading...</span>}
      isLoadingCheck={(keycloak) => {
        return !keycloak.authenticated;
      }}
    >
      {children}
    </ReactKeycloakProvider>
  );
};

export default KeycloakWrapper;
