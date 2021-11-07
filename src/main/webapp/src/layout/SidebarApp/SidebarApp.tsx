import React from "react";
import { NavLink } from "react-router-dom";

import { Nav, NavItem, PageSidebar, NavGroup } from "@patternfly/react-core";

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
        <NavGroup title="Consultas">
          {isElasticsearchEnabled() && (
            <NavItem>
              <NavLink
                to={Paths.contribuyenteList}
                activeClassName="pf-m-current"
              >
                Buscar
              </NavLink>
            </NavItem>
          )}
          <NavItem>
            <NavLink to={Paths.consultaRuc} activeClassName="pf-m-current">
              Número documento
            </NavLink>
          </NavItem>
        </NavGroup>
        <NavGroup title="Padrón reducido">
          <NavItem>
            <NavLink to={Paths.versionList} activeClassName="pf-m-current">
              Versiones
            </NavLink>
          </NavItem>
        </NavGroup>
        {isBasicAuthEnabled() && (
          <VisibilityByPermission hasAny={[Permission.admin]}>
            <NavGroup title="Configuración">
              <NavItem>
                <NavLink
                  to={Paths.settings_userList}
                  activeClassName="pf-m-current"
                >
                  Usuarios
                </NavLink>
              </NavItem>
            </NavGroup>
          </VisibilityByPermission>
        )}
      </Nav>
    );
  };

  return <PageSidebar nav={renderPageNav()} theme={LayoutTheme} />;
};
