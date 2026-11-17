import React, { useState } from "react";
import { useNavigate } from "react-router";
import {
  Dropdown,
  DropdownGroup,
  DropdownItem,
  DropdownList,
  MenuToggle,
  MenuToggleElement,
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

  if (isAuthDisabled()) {
    return null;
  }

  return (
    <Dropdown
      isOpen={isDropdownOpen}
      onSelect={() => setIsDropdownOpen(false)}
      onOpenChange={(isOpen) => setIsDropdownOpen(isOpen)}
      popperProps={{ position: "right" }}
      toggle={(toggleRef: React.Ref<MenuToggleElement>) => (
        <MenuToggle
          ref={toggleRef}
          variant="plainText"
          isExpanded={isDropdownOpen}
          onClick={() => setIsDropdownOpen((current) => !current)}
        >
          {whoAmI.data?.username}
        </MenuToggle>
      )}
    >
      <DropdownGroup key="user-management">
        <DropdownList>
          {isBasicDefaultAuth() && (
            <DropdownItem key="profile" onClick={profile}>
              Profile
            </DropdownItem>
          )}
          <DropdownItem key="logout" onClick={logout}>
            Logout
          </DropdownItem>
        </DropdownList>
      </DropdownGroup>
    </Dropdown>
  );
};
