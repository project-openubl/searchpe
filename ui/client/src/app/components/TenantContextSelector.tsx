import type React from "react";
import { useState } from "react";

import { useNavigate } from "@tanstack/react-router";

import {
  Divider,
  MenuToggle,
  type MenuToggleElement,
  PageSection,
  Select,
  SelectList,
  SelectOption,
} from "@patternfly/react-core";

import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";
import { TENANT_ROLE_LABELS, type TenantRole } from "@app/hooks/useTenantRole";
import { useMyTenantsQuery } from "@app/queries/xhandler/tenants";

/**
 * Page-level selector of the organization (tenant) in the URL (`$tenantId`, ADR 0002):
 * switching keeps the page and re-scopes every query. Hidden with a single membership.
 */
export const TenantContextSelector: React.FC = () => {
  const tenantId = useSelectedTenantId();
  const navigate = useNavigate();
  const [isOpen, setIsOpen] = useState(false);
  const { data: tenants = [] } = useMyTenantsQuery();

  if (tenants.length < 2) {
    return null;
  }

  const selected = tenants.find((tenant) => tenant.id === tenantId);

  return (
    <>
      <PageSection>
        <Select
          id="tenant-context-selector"
          isOpen={isOpen}
          selected={tenantId}
          onSelect={(_event, value) => {
            setIsOpen(false);
            if (typeof value === "string" && value !== tenantId) {
              void navigate({
                to: ".",
                params: (prev) => ({ ...prev, tenantId: value }),
              });
            }
          }}
          onOpenChange={setIsOpen}
          toggle={(toggleRef: React.Ref<MenuToggleElement>) => (
            <MenuToggle
              ref={toggleRef}
              onClick={() => setIsOpen(!isOpen)}
              isExpanded={isOpen}
              aria-label="Seleccionar organización"
            >
              {selected?.name ?? "Seleccionar organización"}
            </MenuToggle>
          )}
        >
          <SelectList>
            {tenants.map((tenant) => (
              <SelectOption
                key={tenant.id}
                value={tenant.id}
                description={
                  TENANT_ROLE_LABELS[tenant.role as TenantRole] ?? tenant.role
                }
              >
                {tenant.name}
              </SelectOption>
            ))}
          </SelectList>
        </Select>
      </PageSection>
      <Divider />
    </>
  );
};
