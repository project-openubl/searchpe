import React, { useState } from "react";

import { useSelector } from "react-redux";
import { RootState } from "store/rootReducer";
import { currentUserSelectors } from "store/currentUser";

import {
  Dropdown,
  DropdownGroup,
  DropdownToggle,
  PageHeaderToolsItem,
} from "@patternfly/react-core";

import { isBasicAuthEnabled, isOidcAuthEnabled } from "Constants";

import { BasicMenuDropdownItems } from "./BasicMenuDropdownItems";
import { OidcMenuDropdownItems } from "./OidcMenuDropdownItems";

export const SSOMenu: React.FC = () => {
  const currentUser = useSelector((state: RootState) =>
    currentUserSelectors.user(state)
  );

  const [isDropdownOpen, setIsDropdownOpen] = useState(false);
  const onDropdownSelect = () => {
    setIsDropdownOpen((current) => !current);
  };
  const onDropdownToggle = (isOpen: boolean) => {
    setIsDropdownOpen(isOpen);
  };

  let authDropdownItems;
  if (isBasicAuthEnabled()) {
    authDropdownItems = <BasicMenuDropdownItems />;
  } else if (isOidcAuthEnabled()) {
    authDropdownItems = <OidcMenuDropdownItems />;
  }

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
            {authDropdownItems}
          </DropdownGroup>,
        ]}
      />
    </PageHeaderToolsItem>
  );
};
