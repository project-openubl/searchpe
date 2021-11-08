import React, { useState } from "react";

import { useSelector } from "react-redux";
import { RootState } from "store/rootReducer";
import { currentUserSelectors } from "store/currentUser";

import {
  Dropdown,
  DropdownGroup,
  DropdownItem,
  DropdownToggle,
  PageHeaderToolsItem,
} from "@patternfly/react-core";
import {
  getAuthFormCookieName,
  isBasicAuthEnabled,
  isOidcAuthEnabled,
  getOidcLogoutPath,
} from "Constants";

export const SSOMenu: React.FC = () => {
  const currentUser = useSelector((state: RootState) =>
    currentUserSelectors.user(state)
  );
  const logout = () => {
    if (isBasicAuthEnabled()) {
      document.cookie = `${getAuthFormCookieName()}=; Max-Age=0`;
      window.location.reload();
    } else if (isOidcAuthEnabled()) {
      window.location.replace(getOidcLogoutPath());
    }
  };

  const [isDropdownOpen, setIsDropdownOpen] = useState(false);
  const onDropdownSelect = () => {
    setIsDropdownOpen((current) => !current);
  };
  const onDropdownToggle = (isOpen: boolean) => {
    setIsDropdownOpen(isOpen);
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
      <Dropdown
        isPlain
        position="right"
        onSelect={onDropdownSelect}
        isOpen={isDropdownOpen}
        toggle={
          <DropdownToggle onToggle={onDropdownToggle}>
            {currentUser?.username}
          </DropdownToggle>
        }
        dropdownItems={[
          <DropdownGroup key="user-management">
            <DropdownItem key="logout" onClick={logout}>
              Logout
            </DropdownItem>
          </DropdownGroup>,
        ]}
      />
    </PageHeaderToolsItem>
  );
};
