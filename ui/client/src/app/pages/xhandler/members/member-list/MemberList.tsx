import type React from "react";
import { useState } from "react";

import {
  Content,
  ContentVariants,
  Flex,
  FlexItem,
  PageSection,
} from "@patternfly/react-core";
import { DataView, DataViewTable } from "@patternfly/react-data-view";

import { dataViewBodyStates } from "@app/components/dataViewStates";
import { DocumentTitle } from "@app/components/DocumentTitle";
import { GatedButton } from "@app/components/GatedButton";
import { TableEmptyState } from "@app/components/TableEmptyState";
import { TenantContextSelector } from "@app/components/TenantContextSelector";
import {
  TENANT_ROLE_LABELS,
  type TenantRole,
  useTenantRole,
} from "@app/hooks/useTenantRole";
import { useMembersListQuery } from "@app/queries/xhandler/members";
import type { DataViewTh } from "@app/utils/dataViewTypes";

import { AddMemberModal } from "./components/AddMemberModal";

const KIND_LABELS: Record<string, string> = {
  user: "Persona",
  client: "Cliente de API",
};

/**
 * The organization's people and API clients with their role (admin). The API answers every
 * member at once, so the table doesn't page.
 */
export const MemberList: React.FC = () => {
  const { disabledProps } = useTenantRole();
  const [isAddOpen, setIsAddOpen] = useState(false);
  const { data, isLoading, error } = useMembersListQuery();

  const columns: DataViewTh[] = [
    { cell: "Miembro", props: { width: 50 } },
    { cell: "Tipo", props: { width: 25 } },
    { cell: "Rol", props: { width: 25 } },
  ];

  const rows = (data ?? []).map((member) => ({
    id: member.id ?? "",
    row: [
      {
        cell: member.subject ?? "-",
        props: { modifier: "truncate" as const },
      },
      { cell: KIND_LABELS[member.kind ?? ""] ?? member.kind ?? "-" },
      {
        cell:
          TENANT_ROLE_LABELS[member.role as TenantRole] ?? member.role ?? "-",
      },
    ],
  }));

  const { activeState, bodyStates } = dataViewBodyStates({
    columnCount: columns.length,
    isLoading,
    error,
    isEmpty: rows.length === 0,
    emptyState: <TableEmptyState title="La organización no tiene miembros" />,
  });

  return (
    <>
      <DocumentTitle title="Miembros" />
      <TenantContextSelector />
      <PageSection>
        <Flex
          justifyContent={{ default: "justifyContentSpaceBetween" }}
          alignItems={{ default: "alignItemsCenter" }}
        >
          <FlexItem>
            <Content component={ContentVariants.h1}>Miembros</Content>
            <Content component={ContentVariants.p}>
              Las personas y los clientes de API (ERP) de esta organización, y
              su rol.
            </Content>
          </FlexItem>
          <FlexItem>
            <GatedButton
              variant="primary"
              onClick={() => setIsAddOpen(true)}
              {...disabledProps("admin")}
            >
              Agregar miembro
            </GatedButton>
          </FlexItem>
        </Flex>
      </PageSection>
      <PageSection>
        <DataView activeState={activeState}>
          <DataViewTable
            aria-label="Tabla de miembros"
            columns={columns}
            rows={rows}
            bodyStates={bodyStates}
          />
        </DataView>
      </PageSection>
      <AddMemberModal isOpen={isAddOpen} onClose={() => setIsAddOpen(false)} />
    </>
  );
};
