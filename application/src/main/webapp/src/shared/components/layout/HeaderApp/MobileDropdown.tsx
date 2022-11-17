import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Dropdown,
  DropdownItem,
  DropdownSeparator,
  KebabToggle,
} from "@patternfly/react-core";
import { HelpIcon } from "@patternfly/react-icons";

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

  const onKebabDropdownToggle = (isOpen: boolean) => {
    setIsKebabDropdownOpen(isOpen);
  };
  const onKebabDropdownSelect = () => {
    setIsKebabDropdownOpen((current) => !current);
  };

  const toggleAboutModal = () => {
    setAboutModalOpen((current) => !current);
  };

  //
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

  //
  let dropdownItems: JSX.Element[] = [];

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
      <DropdownSeparator key="separator" />,
    ];
  }

  dropdownItems = [
    ...dropdownItems,
    <DropdownItem key="about" onClick={toggleAboutModal}>
      <HelpIcon />
      &nbsp;About
    </DropdownItem>,
  ];

  return (
    <>
      <Dropdown
        isPlain
        position="right"
        onSelect={onKebabDropdownSelect}
        toggle={<KebabToggle onToggle={onKebabDropdownToggle} />}
        isOpen={isKebabDropdownOpen}
        dropdownItems={dropdownItems}
      />
      <AppAboutModal isOpen={isAboutModalOpen} onClose={toggleAboutModal} />
    </>
  );
};
