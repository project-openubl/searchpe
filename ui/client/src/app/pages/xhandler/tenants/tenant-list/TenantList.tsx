import type React from "react";

import { Link } from "@tanstack/react-router";

import { Content, ContentVariants, PageSection } from "@patternfly/react-core";
import { DataView, DataViewTable } from "@patternfly/react-data-view";

import { dataViewBodyStates } from "@app/components/dataViewStates";
import { DocumentTitle } from "@app/components/DocumentTitle";
import { TableEmptyState } from "@app/components/TableEmptyState";
import { TENANT_ROLE_LABELS, type TenantRole } from "@app/hooks/useTenantRole";
import { useMyTenantsQuery } from "@app/queries/xhandler/tenants";
import type { DataViewTh } from "@app/utils/dataViewTypes";

/**
 * The organizations (tenants) the user belongs to, the top of partner → tenant → company
 * (ADR 0002). The API answers every membership at once, so the table doesn't page.
 */
export const TenantList: React.FC = () => {
  const { data, isLoading, error } = useMyTenantsQuery();

  const columns: DataViewTh[] = [
    { cell: "Organización", props: { width: 70 } },
    { cell: "Mi rol", props: { width: 30 } },
  ];

  const rows = (data ?? []).map((tenant) => ({
    id: tenant.id ?? "",
    row: [
      {
        cell: tenant.id ? (
          <Link
            to="/organizaciones/$tenantId/empresas"
            params={{ tenantId: tenant.id }}
          >
            {tenant.name}
          </Link>
        ) : (
          tenant.name
        ),
        props: { modifier: "truncate" as const },
      },
      {
        cell: TENANT_ROLE_LABELS[tenant.role as TenantRole] ?? tenant.role,
      },
    ],
  }));

  const { activeState, bodyStates } = dataViewBodyStates({
    columnCount: columns.length,
    isLoading,
    error,
    isEmpty: rows.length === 0,
    emptyState: (
      <TableEmptyState
        title="Aún no perteneces a ninguna organización"
        body="Pide a un administrador de tu organización que te invite."
      />
    ),
  });

  return (
    <>
      <DocumentTitle title="Organizaciones" />
      <PageSection>
        <Content component={ContentVariants.h1}>Organizaciones</Content>
        <Content component={ContentVariants.p}>
          Las organizaciones a las que perteneces y tu rol en cada una.
        </Content>
      </PageSection>
      <PageSection>
        <DataView activeState={activeState}>
          <DataViewTable
            aria-label="Tabla de organizaciones"
            columns={columns}
            rows={rows}
            bodyStates={bodyStates}
          />
        </DataView>
      </PageSection>
    </>
  );
};
