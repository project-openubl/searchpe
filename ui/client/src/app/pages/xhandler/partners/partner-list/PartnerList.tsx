import type React from "react";

import { Link } from "@tanstack/react-router";

import { Content, ContentVariants, PageSection } from "@patternfly/react-core";
import { DataView, DataViewTable } from "@patternfly/react-data-view";

import { dataViewBodyStates } from "@app/components/dataViewStates";
import { DocumentTitle } from "@app/components/DocumentTitle";
import { TableEmptyState } from "@app/components/TableEmptyState";
import {
  isPartnerRole,
  PARTNER_ROLE_LABELS,
  useMyPartnersQuery,
} from "@app/queries/xhandler/partners";
import type { DataViewTh } from "@app/utils/dataViewTypes";

/**
 * The partners (socios) the user belongs to, above their tenants in partner → tenant → company
 * (ADR 0002). The API answers every membership at once, so the table doesn't page.
 */
export const PartnerList: React.FC = () => {
  const { data, isLoading, error } = useMyPartnersQuery();

  const columns: DataViewTh[] = [
    { cell: "Socio", props: { width: 70 } },
    { cell: "Mi rol", props: { width: 30 } },
  ];

  const rows = (data ?? []).map((partner) => ({
    id: partner.id ?? "",
    row: [
      {
        cell: partner.id ? (
          <Link to="/socios/$partnerId" params={{ partnerId: partner.id }}>
            {partner.name}
          </Link>
        ) : (
          partner.name
        ),
        props: { modifier: "truncate" as const },
      },
      {
        cell:
          partner.role && isPartnerRole(partner.role)
            ? PARTNER_ROLE_LABELS[partner.role]
            : (partner.role ?? "-"),
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
        title="No perteneces a ningún socio"
        body="Los socios son empresas de software que administran organizaciones de sus clientes."
      />
    ),
  });

  return (
    <>
      <DocumentTitle title="Socios" />
      <PageSection>
        <Content component={ContentVariants.h1}>Socios</Content>
        <Content component={ContentVariants.p}>
          Los socios a los que perteneces y tu rol en cada uno.
        </Content>
      </PageSection>
      <PageSection>
        <DataView activeState={activeState}>
          <DataViewTable
            aria-label="Tabla de socios"
            columns={columns}
            rows={rows}
            bodyStates={bodyStates}
          />
        </DataView>
      </PageSection>
    </>
  );
};
