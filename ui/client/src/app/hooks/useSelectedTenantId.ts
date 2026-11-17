import { useParams } from "@tanstack/react-router";

/**
 * The tenant in the URL (`/organizaciones/$tenantId/…`, ADR 0002); empty outside tenant pages.
 * The one hook that reads a path param, so queries scope themselves to the tenant.
 */
export const useSelectedTenantId = (): string => {
  const { tenantId } = useParams({ strict: false });
  return tenantId ?? "";
};
