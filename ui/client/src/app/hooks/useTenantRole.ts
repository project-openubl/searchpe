import { useMyTenantsQuery } from "@app/queries/xhandler/tenants";

import { useSelectedTenantId } from "./useSelectedTenantId";

/** Membership roles of a tenant, strongest first (xhandler's `Role`); `support` only reads. */
export const TENANT_ROLES = [
  "owner",
  "admin",
  "operator",
  "viewer",
  "support",
] as const;

export type TenantRole = (typeof TENANT_ROLES)[number];

/** The roles an action can require, as the API's `TenantAccess.enter(…, minimum)` does. */
export type MinimumTenantRole = Exclude<TenantRole, "support">;

export const TENANT_ROLE_LABELS: Record<TenantRole, string> = {
  owner: "Propietario",
  admin: "Administrador",
  operator: "Operador",
  viewer: "Lector",
  support: "Soporte",
};

/** Mirrors `Role#allows`: a stronger role does what a weaker one may; support only reads. */
export const tenantRoleAllows = ({
  role,
  minimum,
}: {
  role: string | undefined;
  minimum: MinimumTenantRole;
}): boolean => {
  if (role === "support") return minimum === "viewer";
  const index = TENANT_ROLES.indexOf(role as TenantRole);
  return index >= 0 && index <= TENANT_ROLES.indexOf(minimum);
};

/**
 * The user's role in the tenant in the URL and the gates it opens (ADR 0004). Like searchpe's
 * permissions, `can` is `true` until the memberships load.
 */
export const useTenantRole = () => {
  const tenantId = useSelectedTenantId();
  const { data, isSuccess } = useMyTenantsQuery();
  const role = data?.find((tenant) => tenant.id === tenantId)?.role;

  const can = (minimum: MinimumTenantRole): boolean =>
    !isSuccess || tenantRoleAllows({ role, minimum });

  const disabledProps = (minimum: MinimumTenantRole) =>
    can(minimum)
      ? {}
      : {
          isAriaDisabled: true,
          tooltipProps: {
            content: `Necesitas el rol ${TENANT_ROLE_LABELS[minimum]} o superior en esta organización.`,
          },
        };

  return { role, can, disabledProps };
};
