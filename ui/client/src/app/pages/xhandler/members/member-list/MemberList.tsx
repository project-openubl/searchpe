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
import { ActionsColumn, type IAction } from "@patternfly/react-table";

import type { MemberView } from "@app/client/xhandler";
import { ConfirmActionModal } from "@app/components/ConfirmActionModal";
import { dataViewBodyStates } from "@app/components/dataViewStates";
import { DocumentTitle } from "@app/components/DocumentTitle";
import { GatedButton } from "@app/components/GatedButton";
import { TableEmptyState } from "@app/components/TableEmptyState";
import { TenantContextSelector } from "@app/components/TenantContextSelector";
import { UnauthorizedState } from "@app/components/UnauthorizedState";
import { useNotifications } from "@app/hooks/useNotifications";
import {
  TENANT_ROLE_LABELS,
  type TenantRole,
  useTenantRole,
} from "@app/hooks/useTenantRole";
import {
  useMemberRemoveMutation,
  useMembersListQuery,
} from "@app/queries/xhandler/members";
import { getMutationErrorMessage } from "@app/utils/apiError";
import type { DataViewTh } from "@app/utils/dataViewTypes";

import { AddMemberModal } from "./components/AddMemberModal";
import { ChangeMemberRoleModal } from "./components/ChangeMemberRoleModal";

const KIND_LABELS: Record<string, string> = {
  user: "Persona",
  client: "Cliente de API",
};

/**
 * The organization's people and API clients with their role (admin), and changing or removing
 * them. Only owners change owners, and the last owner stays (the API refuses with 409). The API
 * answers every member at once, so the table doesn't page.
 */
export const MemberList: React.FC = () => {
  const { can, disabledProps } = useTenantRole();
  const { addNotification } = useNotifications();
  const [isAddOpen, setIsAddOpen] = useState(false);
  const [roleTarget, setRoleTarget] = useState<MemberView | null>(null);
  const [removeTarget, setRemoveTarget] = useState<MemberView | null>(null);
  // GET …/members needs admin (a lesser role gets the unauthorized state, not a failed call)
  // and is unpaged (one row per member): read whole.
  const isAdmin = can("admin");
  const { data, isLoading, error } = useMembersListQuery({
    options: { enabled: isAdmin },
  });

  const removeMember = useMemberRemoveMutation({
    onSuccess: (_data, member) =>
      addNotification({
        variant: "success",
        title: `Se quitó ${member.subject ?? "el miembro"} de la organización`,
      }),
    onError: (err) =>
      addNotification({
        variant: "danger",
        ...getMutationErrorMessage({
          error: err,
          fallback: "No se pudo quitar el miembro",
        }),
      }),
  });

  // Owners are changed by owners only (the API answers 403 owner_required otherwise)
  const ownerGate = disabledProps("owner").tooltipProps;
  const adminGate = disabledProps("admin").tooltipProps;
  const actionsFor = (member: MemberView): IAction[] => {
    const isOwner = member.role === "owner";
    const allowed = isOwner ? can("owner") : can("admin");
    const tooltipProps = isOwner ? ownerGate : adminGate;
    return [
      {
        title: "Cambiar rol",
        onClick: () => setRoleTarget(member),
        isAriaDisabled: !allowed,
        tooltipProps,
      },
      {
        title: "Quitar",
        onClick: () => setRemoveTarget(member),
        isAriaDisabled: !allowed,
        tooltipProps,
      },
    ];
  };

  const columns: DataViewTh[] = [
    { cell: "Miembro", props: { width: 50 } },
    { cell: "Tipo", props: { width: 25 } },
    { cell: "Rol", props: { width: 25 } },
    { cell: "" },
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
      {
        cell: (
          <ActionsColumn
            items={actionsFor(member)}
            popperProps={{ position: "right" }}
          />
        ),
        props: { isActionCell: true },
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
        {isAdmin ? (
          <DataView activeState={activeState}>
            <DataViewTable
              aria-label="Tabla de miembros"
              columns={columns}
              rows={rows}
              bodyStates={bodyStates}
            />
          </DataView>
        ) : (
          <UnauthorizedState description="Necesitas el rol Administrador o superior para ver los miembros de la organización." />
        )}
      </PageSection>
      <AddMemberModal isOpen={isAddOpen} onClose={() => setIsAddOpen(false)} />
      <ChangeMemberRoleModal
        member={roleTarget}
        onClose={() => setRoleTarget(null)}
      />
      <ConfirmActionModal
        isOpen={removeTarget !== null}
        title="¿Quitar al miembro?"
        body={`${removeTarget?.subject ?? "El miembro"} dejará de tener acceso a esta organización.`}
        confirmLabel="Quitar"
        isConfirming={removeMember.isPending}
        onConfirm={() =>
          removeTarget &&
          removeMember.mutate(removeTarget, {
            onSuccess: () => setRemoveTarget(null),
          })
        }
        onCancel={() => setRemoveTarget(null)}
      />
    </>
  );
};
