import type React from "react";
import { useState } from "react";

import { Link, useLocation, useMatchRoute } from "@tanstack/react-router";

import { Nav, NavExpandable, NavItem, NavList } from "@patternfly/react-core";

import { useApiStatus } from "@app/hooks/useApiStatus";
import { useAuth } from "@app/hooks/useAuth";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";
import { useMyTenantsQuery } from "@app/queries/xhandler/tenants";
import { useLastTenantStore } from "@app/stores/useLastTenantStore";

interface INavAppProps {
  onSelect: () => void;
}

/** Top-level URL segment → sidebar group (ADR 0001, 0002). */
const GROUP_OF_SEGMENT: Record<string, string> = {
  organizaciones: "comprobantes",
  socios: "comprobantes",
  padron: "padron",
  perfil: "cuenta",
};

interface IUnavailableItemProps {
  isLoading: boolean;
}

/** Stands in for a product's pages while its backend doesn't answer its status. */
const UnavailableItem: React.FC<IUnavailableItemProps> = ({ isLoading }) => (
  <NavItem preventDefault component="button" isActive={false} disabled>
    {isLoading ? "Comprobando el servicio…" : "Servicio no disponible"}
  </NavItem>
);

export const NavApp: React.FC<INavAppProps> = ({ onSelect }) => {
  const matchRoute = useMatchRoute();
  const { xhandler, searchpe } = useApiStatus();
  const auth = useAuth();

  // Tenant pages carry the tenant in the URL; elsewhere the last one opened (if still a
  // membership) or the only one.
  const selectedTenantId = useSelectedTenantId();
  const lastTenantId = useLastTenantStore((state) => state.lastTenantId);
  // Only with a session: a 404 page outside the private routes renders this shell without one.
  const { data: tenants } = useMyTenantsQuery({
    options: { enabled: xhandler.isAvailable && auth.isAuthenticated() },
  });
  const tenantId =
    selectedTenantId ||
    tenants?.find((tenant) => tenant.id === lastTenantId)?.id ||
    (tenants?.length === 1 ? (tenants[0].id ?? "") : "");

  const group = useLocation({
    select: (location) =>
      GROUP_OF_SEGMENT[location.pathname.split("/")[1]] ?? "inicio",
  });
  // The current page's group opens on load and whenever navigation enters another group.
  const [expandedGroups, setExpandedGroups] = useState<Record<string, boolean>>(
    () => ({ [group]: true, comprobantes: true, padron: true }),
  );
  const [openedGroup, setOpenedGroup] = useState(group);
  if (openedGroup !== group) {
    setOpenedGroup(group);
    setExpandedGroups((prev) => ({ ...prev, [group]: true }));
  }
  const groupProps = (id: string) => ({
    isActive: group === id,
    isExpanded: !!expandedGroups[id],
    onExpand: (_event: React.MouseEvent, isExpanded: boolean) =>
      setExpandedGroups((prev) => ({ ...prev, [id]: isExpanded })),
  });

  return (
    <Nav id="nav-sidebar" aria-label="Navegación" onSelect={onSelect}>
      <NavList>
        <NavItem isActive={!!matchRoute({ to: "/" })}>
          <Link to="/">Inicio</Link>
        </NavItem>

        <NavExpandable
          title="Comprobantes electrónicos"
          {...groupProps("comprobantes")}
        >
          {xhandler.isAvailable ? (
            <>
              <NavItem
                isActive={!!matchRoute({ to: "/organizaciones", fuzzy: false })}
              >
                <Link to="/organizaciones">Organizaciones</Link>
              </NavItem>
              {tenantId ? (
                <>
                  <NavItem
                    isActive={
                      !!matchRoute({
                        to: "/organizaciones/$tenantId/empresas",
                        params: { tenantId },
                        fuzzy: true,
                      })
                    }
                  >
                    <Link
                      to="/organizaciones/$tenantId/empresas"
                      params={{ tenantId }}
                    >
                      Empresas
                    </Link>
                  </NavItem>
                  <NavItem
                    isActive={
                      !!matchRoute({
                        to: "/organizaciones/$tenantId/trabajos",
                        params: { tenantId },
                        fuzzy: true,
                      })
                    }
                  >
                    <Link
                      to="/organizaciones/$tenantId/trabajos"
                      params={{ tenantId }}
                    >
                      Trabajos
                    </Link>
                  </NavItem>
                </>
              ) : null}
            </>
          ) : (
            <UnavailableItem isLoading={xhandler.isLoading} />
          )}
        </NavExpandable>

        {searchpe.isEnabled ? (
          <NavExpandable title="Padrón RUC" {...groupProps("padron")}>
            {searchpe.isAvailable ? (
              <>
                <NavItem
                  isActive={
                    !!matchRoute({ to: "/padron/consulta", fuzzy: true })
                  }
                >
                  <Link to="/padron/consulta">Consulta RUC</Link>
                </NavItem>
                <NavItem
                  isActive={
                    !!matchRoute({ to: "/padron/contribuyentes", fuzzy: true })
                  }
                >
                  <Link to="/padron/contribuyentes">Contribuyentes</Link>
                </NavItem>
                <NavItem
                  isActive={
                    !!matchRoute({ to: "/padron/versiones", fuzzy: true })
                  }
                >
                  <Link to="/padron/versiones">Versiones del padrón</Link>
                </NavItem>
              </>
            ) : (
              <UnavailableItem isLoading={searchpe.isLoading} />
            )}
          </NavExpandable>
        ) : null}

        <NavExpandable title="Cuenta" {...groupProps("cuenta")}>
          <NavItem isActive={!!matchRoute({ to: "/perfil" })}>
            <Link to="/perfil">Mi perfil</Link>
          </NavItem>
        </NavExpandable>
      </NavList>
    </Nav>
  );
};
