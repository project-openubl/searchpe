import React from "react";
import { NavLink } from "react-router-dom";

import { Nav, PageSidebar, NavGroup } from "@patternfly/react-core";
import { css } from "@patternfly/react-styles";

import { Paths } from "Paths";
import {
  isElasticsearchEnabled,
  isBasicAuthEnabled,
  Permission,
} from "Constants";
import { LayoutTheme } from "../LayoutUtils";
import { VisibilityByPermission } from "shared/containers";

export const SidebarApp: React.FC = () => {
  const renderPageNav = () => {
    return (
      <Nav id="nav-sidebar" aria-label="Nav" theme={LayoutTheme}>
        <VisibilityByPermission hasAny={[Permission.admin, Permission.search]}>
          <NavGroup title="Consultas">
            {isElasticsearchEnabled() && (
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
          </NavGroup>
        </VisibilityByPermission>
        <VisibilityByPermission
          hasAny={[Permission.admin, Permission.version_write]}
        >
          <NavGroup title="Padrón reducido">
            <NavLink
              to={Paths.versiones}
              className={({ isActive }) =>
                css("pf-c-nav__link", isActive ? "pf-m-current" : "")
              }
            >
              Versiones
            </NavLink>
          </NavGroup>
        </VisibilityByPermission>
        {isBasicAuthEnabled() && (
          <VisibilityByPermission
            hasAny={[Permission.admin, Permission.user_write]}
          >
            <NavGroup title="Configuración">
              <NavLink
                to={Paths.settings_users}
                className={({ isActive }) =>
                  css("pf-c-nav__link", isActive ? "pf-m-current" : "")
                }
              >
                Usuarios
              </NavLink>
            </NavGroup>
          </VisibilityByPermission>
        )}
      </Nav>
    );
  };

  return <PageSidebar nav={renderPageNav()} theme={LayoutTheme} />;
};
