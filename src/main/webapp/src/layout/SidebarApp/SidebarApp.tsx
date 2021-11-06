import React from "react";
import { NavLink } from "react-router-dom";
import { Nav, NavItem, PageSidebar, NavGroup } from "@patternfly/react-core";

import { Paths } from "Paths";
import { isElasticsearchEnabled, getAuthMethod } from "Constants";
import { LayoutTheme } from "../LayoutUtils";

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
        {getAuthMethod() === "basic" && (
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
        )}
      </Nav>
    );
  };

  return <PageSidebar nav={renderPageNav()} theme={LayoutTheme} />;
};
