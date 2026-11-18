import type React from "react";
import { useState } from "react";

import {
  Content,
  ContentVariants,
  Label,
  Toolbar,
  ToolbarContent,
  ToolbarItem,
} from "@patternfly/react-core";
import { DataView, DataViewTable } from "@patternfly/react-data-view";
import { ActionsColumn, type IAction } from "@patternfly/react-table";

import type { SeriesView } from "@app/client/xhandler";
import { ConfirmActionModal } from "@app/components/ConfirmActionModal";
import { dataViewBodyStates } from "@app/components/dataViewStates";
import { GatedButton } from "@app/components/GatedButton";
import { SunatEnvLabel } from "@app/components/SunatEnvLabel";
import { TableEmptyState } from "@app/components/TableEmptyState";
import { useNotifications } from "@app/hooks/useNotifications";
import { useTenantRole } from "@app/hooks/useTenantRole";
import {
  useSeriesListQuery,
  useSeriesUpdateMutation,
} from "@app/queries/xhandler/series";
import { getMutationErrorMessage } from "@app/utils/apiError";
import type { DataViewTh } from "@app/utils/dataViewTypes";

import { SeriesCreateModal } from "./SeriesCreateModal";
import { SeriesEditModal } from "./SeriesEditModal";
import { tipoComprobanteLabel } from "./seriesForm";

interface ISeriesPanelProps {
  companyId: string;
  sunatEnv: string | undefined;
}

/**
 * The company's series per environment. The API answers them all at once (a handful per
 * company), so the table doesn't page.
 */
export const SeriesPanel: React.FC<ISeriesPanelProps> = ({
  companyId,
  sunatEnv,
}) => {
  const { disabledProps } = useTenantRole();
  const { addNotification } = useNotifications();
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<SeriesView | null>(null);
  const [deactivateTarget, setDeactivateTarget] = useState<SeriesView | null>(
    null,
  );

  const toggle = useSeriesUpdateMutation(companyId, {
    onSuccess: (series) =>
      addNotification({
        variant: "success",
        title: series.active
          ? `Se activó la serie ${series.serie ?? ""}`
          : `Se desactivó la serie ${series.serie ?? ""}`,
      }),
    onError: (error, { body }) =>
      addNotification({
        variant: "danger",
        ...getMutationErrorMessage({
          error,
          fallback: body.active
            ? "No se pudo activar la serie"
            : "No se pudo desactivar la serie",
        }),
      }),
  });

  const actionsFor = (series: SeriesView): IAction[] => [
    {
      title: "Editar",
      onClick: () => setEditTarget(series),
      ...disabledProps("admin"),
    },
    series.active
      ? {
          title: "Desactivar",
          onClick: () => setDeactivateTarget(series),
          ...disabledProps("admin"),
        }
      : {
          title: "Activar",
          onClick: () =>
            toggle.mutate({
              seriesId: series.id ?? "",
              body: { active: true },
            }),
          ...disabledProps("admin"),
        },
  ];
  // GET …/series is unpaged (a company has a handful): read whole.
  const { data, isLoading, error } = useSeriesListQuery({ companyId });

  const columns: DataViewTh[] = [
    { cell: "Serie", props: { width: 15 } },
    { cell: "Tipo de comprobante", props: { width: 30 } },
    { cell: "Siguiente número", props: { width: 15 } },
    { cell: "Ambiente", props: { width: 15 } },
    { cell: "Estado", props: { width: 25 } },
    { cell: "" },
  ];

  const rows = (data ?? []).map((series) => ({
    id: series.id ?? "",
    row: [
      { cell: series.serie ?? "-" },
      {
        cell: tipoComprobanteLabel(series.tipo_comprobante),
        props: { modifier: "truncate" as const },
      },
      { cell: series.next_number?.toLocaleString("es-PE") ?? "-" },
      { cell: <SunatEnvLabel sunatEnv={series.sunat_env} /> },
      {
        cell: (
          <>
            {series.active ? (
              <Label color="green" isCompact>
                Activa
              </Label>
            ) : (
              <Label isCompact>Inactiva</Label>
            )}{" "}
            {series.is_default ? (
              <Label color="blue" isCompact>
                Predeterminada
              </Label>
            ) : null}
          </>
        ),
      },
      {
        cell: (
          <ActionsColumn
            items={actionsFor(series)}
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
    emptyState: <TableEmptyState title="La empresa no tiene series" />,
  });

  return (
    <>
      <Content component={ContentVariants.p}>
        Las series nuevas se crean en el ambiente actual de la empresa (
        <SunatEnvLabel sunatEnv={sunatEnv} />
        ).
      </Content>
      <DataView activeState={activeState}>
        <Toolbar>
          <ToolbarContent>
            <ToolbarItem>
              <GatedButton
                variant="primary"
                onClick={() => setIsCreateOpen(true)}
                {...disabledProps("admin")}
              >
                Nueva serie
              </GatedButton>
            </ToolbarItem>
          </ToolbarContent>
        </Toolbar>
        <DataViewTable
          aria-label="Tabla de series"
          columns={columns}
          rows={rows}
          bodyStates={bodyStates}
        />
      </DataView>
      <SeriesCreateModal
        isOpen={isCreateOpen}
        companyId={companyId}
        onClose={() => setIsCreateOpen(false)}
      />
      <SeriesEditModal
        companyId={companyId}
        series={editTarget}
        onClose={() => setEditTarget(null)}
      />
      <ConfirmActionModal
        isOpen={deactivateTarget !== null}
        title={`¿Desactivar la serie ${deactivateTarget?.serie ?? ""}?`}
        body="Mientras esté desactivada, la emisión rechaza los comprobantes de esta serie. Los ya emitidos no cambian y puedes volver a activarla."
        confirmLabel="Desactivar serie"
        cancelLabel="Volver"
        isConfirming={toggle.isPending}
        onConfirm={() =>
          deactivateTarget &&
          toggle.mutate(
            { seriesId: deactivateTarget.id ?? "", body: { active: false } },
            { onSuccess: () => setDeactivateTarget(null) },
          )
        }
        onCancel={() => setDeactivateTarget(null)}
      />
    </>
  );
};
