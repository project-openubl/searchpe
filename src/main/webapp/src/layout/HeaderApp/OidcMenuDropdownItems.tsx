import React from "react";
import { useKeycloak } from "@react-keycloak/web";
import { DropdownItem } from "@patternfly/react-core";

export const OidcMenuDropdownItems: React.FC = () => {
  const { keycloak } = useKeycloak();

  return (
    <>
      <DropdownItem
        key="user_management"
        component="button"
        onClick={() => keycloak.accountManagement()}
      >
        Profile
      </DropdownItem>
      <DropdownItem key="logout" onClick={() => keycloak.logout()}>
        Logout
      </DropdownItem>
    </>
  );
};
