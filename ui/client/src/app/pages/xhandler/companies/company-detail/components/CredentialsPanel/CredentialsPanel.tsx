import type React from "react";
import { useState } from "react";

import dayjs from "dayjs";

import { Content, ContentVariants, Label } from "@patternfly/react-core";
import { DataView, DataViewTable } from "@patternfly/react-data-view";
import { ActionsColumn } from "@patternfly/react-table";

import { dataViewBodyStates } from "@app/components/dataViewStates";
import { RENDER_DATETIME_FORMAT } from "@app/constants";
import { useTenantRole } from "@app/hooks/useTenantRole";
import {
  CREDENTIAL_KINDS,
  type CredentialKind,
  useCredentialsListQuery,
} from "@app/queries/xhandler/credentials";
import type { DataViewTh } from "@app/utils/dataViewTypes";

import { CREDENTIAL_PRESENTATION } from "./credentialForms";
import { CredentialModal } from "./CredentialModal";

interface ICredentialsPanelProps {
  companyId: string;
}

/**
 * One row per credential kind, set or not. Secrets are write-only: the API answers a masked
 * label and the update time, and the UI shows nothing more.
 */
export const CredentialsPanel: React.FC<ICredentialsPanelProps> = ({
  companyId,
}) => {
  const { can, disabledProps } = useTenantRole();
  const [editing, setEditing] = useState<CredentialKind | null>(null);
  const { data, isLoading, error } = useCredentialsListQuery({ companyId });
  const stored = new Map((data ?? []).map((item) => [item.kind, item]));

  const columns: DataViewTh[] = [
    { cell: "Credencial", props: { width: 30 } },
    { cell: "Estado", props: { width: 20 } },
    { cell: "Identificador", props: { width: 30 } },
    { cell: "Actualizada", props: { width: 20 } },
    { cell: "" },
  ];

  const rows = CREDENTIAL_KINDS.map((kind) => {
    const credential = stored.get(kind);
    return {
      id: kind,
      row: [
        { cell: CREDENTIAL_PRESENTATION[kind].title },
        {
          cell: credential ? (
            <Label color="green" isCompact>
              Configurada
            </Label>
          ) : (
            <Label isCompact>Sin configurar</Label>
          ),
        },
        {
          cell: credential?.label || "-",
          props: { modifier: "truncate" as const },
        },
        {
          cell: credential?.updated_at
            ? dayjs(credential.updated_at).format(RENDER_DATETIME_FORMAT)
            : "-",
        },
        {
          cell: (
            <ActionsColumn
              items={[
                {
                  title: credential ? "Reemplazar" : "Configurar",
                  onClick: () => setEditing(kind),
                  isAriaDisabled: !can("admin"),
                  tooltipProps: disabledProps("admin").tooltipProps,
                },
              ]}
              popperProps={{ position: "right" }}
            />
          ),
          props: { isActionCell: true },
        },
      ],
    };
  });

  const { activeState, bodyStates } = dataViewBodyStates({
    columnCount: columns.length,
    isLoading,
    error,
  });

  return (
    <>
      <Content component={ContentVariants.p}>
        Las claves se guardan cifradas y nunca se muestran; para cambiarlas,
        escribe las nuevas.
      </Content>
      <DataView activeState={activeState}>
        <DataViewTable
          aria-label="Tabla de credenciales"
          columns={columns}
          rows={rows}
          bodyStates={bodyStates}
        />
      </DataView>
      <CredentialModal
        kind={editing}
        companyId={companyId}
        onClose={() => setEditing(null)}
      />
    </>
  );
};
