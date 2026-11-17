import React, { useState } from "react";
import { useNavigate } from "react-router";
import {
  Divider,
  Dropdown,
  DropdownItem,
  DropdownList,
  MenuToggle,
  MenuToggleElement,
} from "@patternfly/react-core";
import { EllipsisVIcon, HelpIcon } from "@patternfly/react-icons";

import {
  getAuthFormCookieName,
  getOidcLogoutPath,
  isAuthDisabled,
  isBasicDefaultAuth,
  isOidcDefaultAuth,
} from "Constants";
import { Paths } from "Paths";

import { AppAboutModal } from "../AppAboutModal";

export const MobileDropdown: React.FC = () => {
  const navigate = useNavigate();

  const [isKebabDropdownOpen, setIsKebabDropdownOpen] = useState(false);
  const [isAboutModalOpen, setAboutModalOpen] = useState(false);

  const toggleAboutModal = () => {
    setAboutModalOpen((current) => !current);
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

  let dropdownItems: React.ReactElement[] = [];

  if (!isAuthDisabled()) {
    if (isBasicDefaultAuth()) {
      dropdownItems = [
        ...dropdownItems,
        <DropdownItem key="profile" onClick={profile}>
          Profile
        </DropdownItem>,
      ];
    }

    dropdownItems = [
      ...dropdownItems,
      <DropdownItem key="logout" onClick={logout}>
        Logout
      </DropdownItem>,
      <Divider component="li" key="separator" />,
    ];
  }

  dropdownItems = [
    ...dropdownItems,
    <DropdownItem key="about" onClick={toggleAboutModal} icon={<HelpIcon />}>
      About
    </DropdownItem>,
  ];

  return (
    <>
      <Dropdown
        isOpen={isKebabDropdownOpen}
        onSelect={() => setIsKebabDropdownOpen(false)}
        onOpenChange={(isOpen) => setIsKebabDropdownOpen(isOpen)}
        popperProps={{ position: "right" }}
        toggle={(toggleRef: React.Ref<MenuToggleElement>) => (
          <MenuToggle
            ref={toggleRef}
            variant="plain"
            aria-label="Application menu"
            isExpanded={isKebabDropdownOpen}
            onClick={() => setIsKebabDropdownOpen((current) => !current)}
            icon={<EllipsisVIcon />}
          />
        )}
      >
        <DropdownList>{dropdownItems}</DropdownList>
      </Dropdown>
      <AppAboutModal isOpen={isAboutModalOpen} onClose={toggleAboutModal} />
    </>
  );
};
