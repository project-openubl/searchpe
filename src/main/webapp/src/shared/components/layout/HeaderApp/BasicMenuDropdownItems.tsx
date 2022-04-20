import React from "react";
import { useNavigate } from "react-router-dom";
import { DropdownItem, DropdownSeparator } from "@patternfly/react-core";
import { Paths } from "Paths";
import { getAuthFormCookieName } from "Constants";

export const BasicMenuDropdownItems: React.FC = () => {
  const navigate = useNavigate();
  const profile = () => {
    navigate(Paths.profile);
  };

  const logout = () => {
    document.cookie = `${getAuthFormCookieName()}=; Max-Age=0`;
    window.location.reload();
  };

  return (
    <>
      <DropdownItem key="profile" onClick={profile}>
        Profile
      </DropdownItem>
      <DropdownSeparator key="separator" />
      <DropdownItem key="logout" onClick={logout}>
        Logout
      </DropdownItem>
    </>
  );
};
