import React from "react";
import {
  Avatar,
  Brand,
  Button,
  ButtonVariant,
  Masthead,
  MastheadBrand,
  MastheadContent,
  MastheadLogo,
  MastheadMain,
  MastheadToggle,
  PageToggleButton,
  Toolbar,
  ToolbarContent,
  ToolbarGroup,
  ToolbarItem,
} from "@patternfly/react-core";
import { HelpIcon } from "@patternfly/react-icons";

import { AppAboutModalState } from "../AppAboutModalState";
import { UserDropdown } from "./UserDropdown";
import { MobileDropdown } from "./MobileDropdown";

// PF6 mastheads are light; the original navbar logo has white text (made for the PF4 dark header)
import navBrandImage from "images/logo-navbar-light.svg";
import imgAvatar from "images/avatar.svg";

export const HeaderApp: React.FC = () => {
  const toolbar = (
    <Toolbar id="toolbar" isStatic>
      <ToolbarContent>
        <ToolbarGroup
          variant="action-group-plain"
          align={{ default: "alignEnd" }}
          gap={{ default: "gapNone", md: "gapMd" }}
        >
          {/* the help icon button is only visible on desktop sizes and replaced by a kebab dropdown for other sizes */}
          <ToolbarGroup
            variant="action-group-plain"
            visibility={{ default: "hidden", lg: "visible" }}
          >
            <ToolbarItem>
              <AppAboutModalState>
                {({ toggleModal }) => (
                  <Button
                    id="aboutButton"
                    aria-label="about-button"
                    variant={ButtonVariant.plain}
                    onClick={toggleModal}
                    icon={<HelpIcon />}
                  />
                )}
              </AppAboutModalState>
            </ToolbarItem>
          </ToolbarGroup>
          {/* this kebab dropdown replaces the icon buttons and is hidden for desktop sizes */}
          <ToolbarItem visibility={{ lg: "hidden" }}>
            <MobileDropdown />
          </ToolbarItem>
          {/* this user dropdown is hidden on mobile sizes */}
          <ToolbarItem
            id="user-dropdown"
            visibility={{ default: "hidden", md: "visible" }}
          >
            <UserDropdown />
          </ToolbarItem>
        </ToolbarGroup>
        <ToolbarItem>
          <Avatar src={imgAvatar} alt="Avatar image" size="md" />
        </ToolbarItem>
      </ToolbarContent>
    </Toolbar>
  );

  return (
    <Masthead>
      <MastheadMain>
        <MastheadToggle>
          <PageToggleButton isHamburgerButton aria-label="Global navigation" />
        </MastheadToggle>
        <MastheadBrand>
          <MastheadLogo href="/">
            <Brand
              src={navBrandImage}
              alt="brand"
              heights={{ default: "36px" }}
            />
          </MastheadLogo>
        </MastheadBrand>
      </MastheadMain>
      <MastheadContent>{toolbar}</MastheadContent>
    </Masthead>
  );
};
