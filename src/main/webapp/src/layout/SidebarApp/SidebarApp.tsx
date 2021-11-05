import React from "react";
import { NavLink } from "react-router-dom";
import { Nav, NavItem, PageSidebar, NavList } from "@patternfly/react-core";

import { Paths } from "Paths";
import { LayoutTheme } from "../LayoutUtils";

export const SidebarApp: React.FC = () => {
  const renderPageNav = () => {
    return (
      <Nav id="nav-sidebar" aria-label="Nav" theme={LayoutTheme}>
        <NavList>
          <NavItem>
            <NavLink to={Paths.consultaRuc} activeClassName="pf-m-current">
              RUC
            </NavLink>
          </NavItem>
          <NavItem>
            <NavLink
              to={Paths.contribuyenteList}
              activeClassName="pf-m-current"
            >
              Search
            </NavLink>
          </NavItem>
          <NavItem>
            <NavLink to={Paths.versionList} activeClassName="pf-m-current">
              Versions
            </NavLink>
          </NavItem>
        </NavList>
      </Nav>
    );
  };

  return <PageSidebar nav={renderPageNav()} theme={LayoutTheme} />;
};
