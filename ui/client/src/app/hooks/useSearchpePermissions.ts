import { useWhoamiQuery } from "@app/queries/searchpe/whoami";

/** searchpe's permissions (realm roles of the shared realm), from `Permission.java`. */
export type SearchpePermission = "search" | "version:write" | "admin:app";

const PERMISSION_LABELS: Record<SearchpePermission, string> = {
  search: "search",
  "version:write": "version:write",
  "admin:app": "admin:app",
};

/** `admin:app` allows everything, as searchpe's `@RolesAllowed` lists do. */
export const hasSearchpePermission = ({
  permissions,
  permission,
}: {
  permissions: readonly string[];
  permission: SearchpePermission;
}): boolean =>
  permissions.includes(permission) || permissions.includes("admin:app");

/**
 * What the user may do in searchpe, from `whoami` (ADR 0004). `can` is `true` until whoami
 * loads, so nothing flickers disabled; the server still enforces every call.
 */
export const useSearchpePermissions = () => {
  const { data, isSuccess } = useWhoamiQuery();
  const permissions = data?.permissions ?? [];
  // With `searchpe.disable.authorization=true` searchpe checks nothing and whoami answers the
  // anonymous identity (no name, no roles); an authenticated whoami always has a name.
  const isAuthorizationDisabled = isSuccess && !data?.username;

  const can = (permission: SearchpePermission): boolean =>
    !isSuccess ||
    isAuthorizationDisabled ||
    hasSearchpePermission({ permissions, permission });

  /** Spread onto an action (`GatedButton`, a row action) to disable it with the reason. */
  const disabledProps = (permission: SearchpePermission) =>
    can(permission)
      ? {}
      : {
          isAriaDisabled: true,
          tooltipProps: {
            content: `Necesitas el permiso ${PERMISSION_LABELS[permission]} en searchpe.`,
          },
        };

  return { can, disabledProps, permissions };
};
