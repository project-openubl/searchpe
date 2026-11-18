import type React from "react";
import { useState } from "react";

import { Link } from "@tanstack/react-router";
import dayjs from "dayjs";

import {
  Breadcrumb,
  BreadcrumbItem,
  Content,
  ContentVariants,
  PageSection,
  PaginationVariant,
} from "@patternfly/react-core";
import {
  DataView,
  DataViewFilters,
  DataViewTable,
  DataViewToolbar,
} from "@patternfly/react-data-view";
import { ActionsColumn, type IAction } from "@patternfly/react-table";

import type { ReceptionView } from "@app/client/xhandler";
import { ClearFiltersButton } from "@app/components/ClearFiltersButton";
import { dataViewBodyStates } from "@app/components/dataViewStates";
import { DateFilter } from "@app/components/DateFilter";
import { DocumentTitle } from "@app/components/DocumentTitle";
import { ListPagination } from "@app/components/ListPagination";
import { TableEmptyState } from "@app/components/TableEmptyState";
import { RENDER_DATE_FORMAT } from "@app/constants";
import { useNotifications } from "@app/hooks/useNotifications";
import { usePersistentState } from "@app/hooks/usePersistentState";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";
import { useSuspenseCompanyDetailQuery } from "@app/queries/xhandler/companies";
import {
  hasReceptionFile,
  RECEPTION_FILE_KINDS,
  useReceptionFileDownloadMutation,
  useReceptionsListQuery,
} from "@app/queries/xhandler/receptions";
import { getMutationErrorMessage } from "@app/utils/apiError";
import type { DataViewTh } from "@app/utils/dataViewTypes";
import {
  formatTotal,
  tipoComprobanteLabel,
} from "@app/utils/documentPresentation";
import { estimateItemCount, toPageParams } from "@app/utils/listState";

import { ReceptionDetailModal } from "./components/ReceptionDetailModal";
import { ReceptionValidityLabel } from "./components/ReceptionValidityLabel";
import { RECEPTION_FILE_LABELS } from "./receptionFiles";
import { RECEPTION_LIST_STATE } from "./receptionListState";

interface IReceptionListProps {
  companyId: string;
}

/** Supplier documents received by a company (DESIGN.md §11.5), newest issue date first. */
export const ReceptionList: React.FC<IReceptionListProps> = ({ companyId }) => {
  const tenantId = useSelectedTenantId();
  const { addNotification } = useNotifications();
  const { data: company } = useSuspenseCompanyDetailQuery({ companyId });
  const [selected, setSelected] = useState<ReceptionView | null>(null);

  const [pagination, setPagination] = usePersistentState({
    ...RECEPTION_LIST_STATE.pagination,
    storage: "url",
  });
  const [filters, setFilters] = usePersistentState({
    ...RECEPTION_LIST_STATE.filters,
    storage: "url",
  });
  const toFirstPage = () => setPagination((prev) => ({ ...prev, page: 1 }));
  const clearAllFilters = () => {
    setFilters(RECEPTION_LIST_STATE.filters.defaultValue);
    toFirstPage();
  };

  const { data, isLoading, error } = useReceptionsListQuery({
    companyId,
    params: {
      ...toPageParams(pagination),
      from: filters.desde || undefined,
      to: filters.hasta || undefined,
    },
  });
  const receptions = data?.items ?? [];

  const download = useReceptionFileDownloadMutation({
    onError: (err) =>
      addNotification({
        variant: "danger",
        ...getMutationErrorMessage({
          error: err,
          fallback: "No se pudo descargar el archivo",
        }),
      }),
  });
  const downloadingKind =
    download.isPending && download.variables.reception.id === selected?.id
      ? download.variables.kind
      : undefined;

  const actionsFor = (reception: ReceptionView): IAction[] => [
    { title: "Ver detalle", onClick: () => setSelected(reception) },
    ...RECEPTION_FILE_KINDS.filter((kind) =>
      hasReceptionFile({ reception, kind }),
    ).map((kind) => ({
      title: `Descargar ${RECEPTION_FILE_LABELS[kind]}`,
      onClick: () => download.mutate({ companyId, reception, kind }),
    })),
  ];

  const columns: DataViewTh[] = [
    { cell: "Comprobante", props: { width: 15 } },
    { cell: "Tipo", props: { width: 20 } },
    { cell: "RUC del proveedor", props: { width: 15 } },
    { cell: "Emisión", props: { width: 15 } },
    { cell: "Total", props: { width: 15 } },
    { cell: "Validez en SUNAT", props: { width: 20 } },
    { cell: "" },
  ];

  const rows = receptions.map((reception) => ({
    id: reception.id ?? "",
    row: [
      { cell: reception.serie_numero ?? "-" },
      {
        cell: tipoComprobanteLabel(reception.tipo_comprobante),
        props: { modifier: "truncate" as const },
      },
      { cell: reception.supplier_ruc ?? "-" },
      {
        cell: reception.fecha_emision
          ? dayjs(reception.fecha_emision).format(RENDER_DATE_FORMAT)
          : "-",
      },
      {
        cell: formatTotal({ total: reception.total, moneda: reception.moneda }),
      },
      { cell: <ReceptionValidityLabel state={reception.validity_state} /> },
      {
        cell: (
          <ActionsColumn
            items={actionsFor(reception)}
            popperProps={{ position: "right" }}
          />
        ),
        props: { isActionCell: true },
      },
    ],
  }));

  const isFiltered = Boolean(filters.desde) || Boolean(filters.hasta);

  const { activeState, bodyStates } = dataViewBodyStates({
    columnCount: columns.length,
    isLoading,
    error,
    isEmpty: rows.length === 0,
    emptyState: (
      <TableEmptyState
        title="No hay comprobantes recibidos"
        body="Los comprobantes de proveedores dirigidos a esta empresa aparecerán aquí."
        isFiltered={isFiltered}
        onClearFilters={clearAllFilters}
      />
    ),
  });

  const pager = (variant: PaginationVariant) => (
    <ListPagination
      variant={variant}
      itemCount={estimateItemCount({ pagination, pageSize: receptions.length })}
      isCountEstimated
      pagination={pagination}
      onPaginationChange={setPagination}
    />
  );

  return (
    <>
      <DocumentTitle title={`Recepciones · ${company.ruc ?? ""}`} />
      <PageSection>
        <Breadcrumb>
          <BreadcrumbItem>
            <Link to="/organizaciones/$tenantId/empresas" params={{ tenantId }}>
              Empresas
            </Link>
          </BreadcrumbItem>
          <BreadcrumbItem>
            <Link
              to="/organizaciones/$tenantId/empresas/$companyId"
              params={{ tenantId, companyId }}
            >
              {company.ruc}
            </Link>
          </BreadcrumbItem>
          <BreadcrumbItem isActive>Recepciones</BreadcrumbItem>
        </Breadcrumb>
        <Content component={ContentVariants.h1}>Recepciones</Content>
        <Content component={ContentVariants.p}>
          Comprobantes de proveedores recibidos por{" "}
          {company.razon_social || company.ruc}, con su validez en SUNAT.
        </Content>
      </PageSection>
      <PageSection>
        <DataView activeState={activeState}>
          <DataViewToolbar
            clearAllFilters={clearAllFilters}
            customLabelGroupContent={
              <ClearFiltersButton onClick={clearAllFilters} />
            }
            filters={
              <DataViewFilters
                values={filters}
                onChange={(_key, next) => {
                  setFilters((prev) => ({ ...prev, ...next }));
                  toFirstPage();
                }}
              >
                <DateFilter
                  filterId="desde"
                  title="Emitido desde"
                  placeholder="Emitido desde"
                />
                <DateFilter
                  filterId="hasta"
                  title="Emitido hasta"
                  placeholder="Emitido hasta"
                />
              </DataViewFilters>
            }
            pagination={pager(PaginationVariant.top)}
          />
          <DataViewTable
            aria-label="Tabla de recepciones"
            columns={columns}
            rows={rows}
            bodyStates={bodyStates}
          />
          <DataViewToolbar pagination={pager(PaginationVariant.bottom)} />
        </DataView>
      </PageSection>

      {selected ? (
        <ReceptionDetailModal
          reception={selected}
          downloadingKind={downloadingKind}
          onDownload={(kind) =>
            download.mutate({ companyId, reception: selected, kind })
          }
          onClose={() => setSelected(null)}
        />
      ) : null}
    </>
  );
};
