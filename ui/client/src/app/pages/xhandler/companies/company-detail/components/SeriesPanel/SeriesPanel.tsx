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

import { dataViewBodyStates } from "@app/components/dataViewStates";
import { GatedButton } from "@app/components/GatedButton";
import { SunatEnvLabel } from "@app/components/SunatEnvLabel";
import { TableEmptyState } from "@app/components/TableEmptyState";
import { useTenantRole } from "@app/hooks/useTenantRole";
import { useSeriesListQuery } from "@app/queries/xhandler/series";
import type { DataViewTh } from "@app/utils/dataViewTypes";

import { SeriesCreateModal } from "./SeriesCreateModal";
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
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const { data, isLoading, error } = useSeriesListQuery({ companyId });

  const columns: DataViewTh[] = [
    { cell: "Serie", props: { width: 15 } },
    { cell: "Tipo de comprobante", props: { width: 30 } },
    { cell: "Siguiente número", props: { width: 15 } },
    { cell: "Ambiente", props: { width: 15 } },
    { cell: "Estado", props: { width: 25 } },
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
    </>
  );
};
