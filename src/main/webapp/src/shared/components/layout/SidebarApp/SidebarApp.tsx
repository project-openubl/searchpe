import React from "react";
import { NavLink } from "react-router-dom";

import { Nav, PageSidebar, NavList } from "@patternfly/react-core";
import { css } from "@patternfly/react-styles";

import { Paths } from "Paths";
import { isAdvancedSearchEnabled, isAuthDisabled, Permission } from "Constants";
import { LayoutTheme } from "../LayoutUtils";
import { VisibilityByPermission } from "shared/containers";

export const SidebarApp: React.FC = () => {
  const renderPageNav = () => {
    return (
      <Nav id="nav-sidebar" aria-label="Nav" theme={LayoutTheme}>
        <NavList title="Consultas">
          <VisibilityByPermission
            hasAny={[Permission.admin, Permission.search]}
          >
            {isAdvancedSearchEnabled() && (
              <NavLink
                to={Paths.contribuyentes}
                className={({ isActive }) =>
                  css("pf-c-nav__link", isActive ? "pf-m-current" : "")
                }
              >
                Buscar
              </NavLink>
            )}
            <NavLink
              to={Paths.consultaRuc}
              className={({ isActive }) =>
                css("pf-c-nav__link", isActive ? "pf-m-current" : "")
              }
            >
              Número documento
            </NavLink>
          </VisibilityByPermission>
          <VisibilityByPermission
            hasAny={[Permission.admin, Permission.version_write]}
          >
            <NavLink
              to={Paths.versiones}
              className={({ isActive }) =>
                css("pf-c-nav__link", isActive ? "pf-m-current" : "")
              }
            >
              Versiones
            </NavLink>
          </VisibilityByPermission>
          {!isAuthDisabled() && (
            <VisibilityByPermission
              hasAny={[Permission.admin, Permission.user_write]}
            >
              <NavLink
                to={Paths.users}
                className={({ isActive }) =>
                  css("pf-c-nav__link", isActive ? "pf-m-current" : "")
                }
              >
                Usuarios
              </NavLink>
            </VisibilityByPermission>
          )}
        </NavList>
      </Nav>
    );
  };

  return <PageSidebar nav={renderPageNav()} theme={LayoutTheme} />;
};
