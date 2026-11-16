import React from "react";
import { NavLink, useMatch } from "react-router";

import {
  Nav,
  NavItem,
  NavList,
  PageSidebar,
  PageSidebarBody,
} from "@patternfly/react-core";

import { Paths } from "Paths";
import { isAdvancedSearchEnabled, isAuthDisabled, Permission } from "Constants";
import { VisibilityByPermission } from "shared/containers";

interface SidebarNavItemProps {
  to: string;
  children: React.ReactNode;
}

const SidebarNavItem: React.FC<SidebarNavItemProps> = ({ to, children }) => {
  const match = useMatch({ path: to, end: false });
  return (
    <NavItem isActive={!!match}>
      <NavLink to={to}>{children}</NavLink>
    </NavItem>
  );
};

export const SidebarApp: React.FC = () => {
  return (
    <PageSidebar>
      <PageSidebarBody>
        <Nav id="nav-sidebar" aria-label="Nav">
          <NavList>
            <VisibilityByPermission
              hasAny={[Permission.admin, Permission.search]}
            >
              {isAdvancedSearchEnabled() && (
                <SidebarNavItem to={Paths.contribuyentes}>
                  Buscar
                </SidebarNavItem>
              )}
              <SidebarNavItem to={Paths.consultaRuc}>
                Número documento
              </SidebarNavItem>
            </VisibilityByPermission>
            <VisibilityByPermission
              hasAny={[Permission.admin, Permission.version_write]}
            >
              <SidebarNavItem to={Paths.versiones}>Versiones</SidebarNavItem>
            </VisibilityByPermission>
            {!isAuthDisabled() && (
              <VisibilityByPermission
                hasAny={[Permission.admin, Permission.user_write]}
              >
                <SidebarNavItem to={Paths.users}>Usuarios</SidebarNavItem>
              </VisibilityByPermission>
            )}
          </NavList>
        </Nav>
      </PageSidebarBody>
    </PageSidebar>
  );
};
