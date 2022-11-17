import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Dropdown,
  DropdownGroup,
  DropdownItem,
  DropdownToggle,
  PageHeaderToolsItem,
} from "@patternfly/react-core";

import { useWhoAmIQuery } from "queries/whoami";

import {
  getAuthFormCookieName,
  getOidcLogoutPath,
  isAuthDisabled,
  isBasicDefaultAuth,
  isOidcDefaultAuth,
} from "Constants";

import { Paths } from "Paths";

export const UserDropdown: React.FC = () => {
  const navigate = useNavigate();
  const whoAmI = useWhoAmIQuery();

  const [isDropdownOpen, setIsDropdownOpen] = useState(false);
  const onDropdownSelect = () => {
    setIsDropdownOpen((current) => !current);
  };
  const onDropdownToggle = (isOpen: boolean) => {
    setIsDropdownOpen(isOpen);
  };

  const profile = () => {
    navigate(Paths.profile);
  };

  const logout = () => {
    if (isBasicDefaultAuth()) {
      document.cookie = `${getAuthFormCookieName()}=; Max-Age=0`;
      window.location.reload();
    } else if (isOidcDefaultAuth()) {
      window.location.replace(getOidcLogoutPath());
    }
  };

  return (
    <PageHeaderToolsItem
      id="user-dropdown"
      visibility={{
        default: "hidden",
        md: "visible",
        lg: "visible",
        xl: "visible",
        "2xl": "visible",
      }} /** this user dropdown is hidden on mobile sizes */
    >
      {!isAuthDisabled() && (
        <Dropdown
          isPlain
          position="right"
          onSelect={onDropdownSelect}
          isOpen={isDropdownOpen}
          toggle={
            <DropdownToggle onToggle={onDropdownToggle}>
              {whoAmI.data?.username}
            </DropdownToggle>
          }
          dropdownItems={[
            <DropdownGroup key="user-management">
              {isBasicDefaultAuth() && (
                <DropdownItem key="profile" onClick={profile}>
                  Profile
                </DropdownItem>
              )}
              <DropdownItem key="logout" onClick={logout}>
                Logout
              </DropdownItem>
            </DropdownGroup>,
          ]}
        />
      )}
    </PageHeaderToolsItem>
  );
};
