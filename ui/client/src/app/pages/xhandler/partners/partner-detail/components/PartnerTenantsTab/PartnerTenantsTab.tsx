import type React from "react";
import { useState } from "react";

import { Link } from "@tanstack/react-router";
import dayjs from "dayjs";

import { PaginationVariant } from "@patternfly/react-core";
import {
  DataView,
  DataViewFilters,
  DataViewTable,
  DataViewToolbar,
} from "@patternfly/react-data-view";

import { ClearFiltersButton } from "@app/components/ClearFiltersButton";
import { dataViewBodyStates } from "@app/components/dataViewStates";
import { GatedButton } from "@app/components/GatedButton";
import { ListPagination } from "@app/components/ListPagination";
import { SingleSelectFilter } from "@app/components/SingleSelectFilter";
import { TableEmptyState } from "@app/components/TableEmptyState";
import { usePersistentState } from "@app/hooks/usePersistentState";
import { usePartnerTenantsQuery } from "@app/queries/xhandler/partners";
import type { DataViewTh } from "@app/utils/dataViewTypes";

import {
  PARTNER_TENANT_LIST_STATE,
  usageMonths,
} from "../../partnerTenantListState";
import { CreatePartnerTenantModal } from "../CreatePartnerTenantModal";

interface IPartnerTenantsTabProps {
  partnerId: string;
  /** Disabled when the user isn't an admin of the partner. */
  gateProps: { isAriaDisabled?: boolean; tooltipProps?: { content: string } };
}

const TENANT_STATUS_LABELS: Record<string, string> = {
  active: "Activa",
  suspended: "Suspendida",
  exited: "Dada de baja",
};

const MONTH_OPTIONS = usageMonths().map((month) => ({
  value: month,
  label: dayjs(`${month}-01`).format("MMMM [de] YYYY"),
}));

const formatCount = (value: number | undefined): string =>
  (value ?? 0).toLocaleString("es-PE");

/** The partner's tenants with their documents in a month (DESIGN.md §14). */
export const PartnerTenantsTab: React.FC<IPartnerTenantsTabProps> = ({
  partnerId,
  gateProps,
}) => {
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [pagination, setPagination] = usePersistentState({
    ...PARTNER_TENANT_LIST_STATE.pagination,
    storage: "url",
  });
  const [filters, setFilters] = usePersistentState({
    ...PARTNER_TENANT_LIST_STATE.filters,
    storage: "url",
  });
  const toFirstPage = () => setPagination((prev) => ({ ...prev, page: 1 }));
  const clearAllFilters = () => {
    setFilters(PARTNER_TENANT_LIST_STATE.filters.defaultValue);
    toFirstPage();
  };

  const { data, isLoading, error } = usePartnerTenantsQuery({
    partnerId,
    params: { month: filters.mes[0] },
  });
  // Unpaged by the API: read whole, paged on screen.
  const tenants = data ?? [];
  const start = (pagination.page - 1) * pagination.perPage;
  const pageTenants = tenants.slice(start, start + pagination.perPage);

  const columns: DataViewTh[] = [
    { cell: "Organización", props: { width: 35 } },
    { cell: "Estado", props: { width: 15 } },
    { cell: "Plan", props: { width: 15 } },
    { cell: "Documentos en producción", props: { width: 20 } },
    { cell: "Otros documentos", props: { width: 15 } },
  ];

  const rows = pageTenants.map((tenant) => ({
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
        cell: TENANT_STATUS_LABELS[tenant.status ?? ""] ?? tenant.status ?? "-",
      },
      { cell: tenant.plan ?? "-" },
      { cell: formatCount(tenant.production_documents) },
      { cell: formatCount(tenant.other_documents) },
    ],
  }));

  const { activeState, bodyStates } = dataViewBodyStates({
    columnCount: columns.length,
    isLoading,
    error,
    isEmpty: rows.length === 0,
    emptyState: (
      <TableEmptyState
        title="El socio aún no tiene organizaciones"
        body="Las organizaciones que crees para tus clientes aparecerán aquí."
      />
    ),
  });

  const pager = (variant: PaginationVariant) => (
    <ListPagination
      variant={variant}
      itemCount={tenants.length}
      pagination={pagination}
      onPaginationChange={setPagination}
    />
  );

  return (
    <>
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
              <SingleSelectFilter
                filterId="mes"
                title="Mes"
                placeholder="Mes actual"
                options={MONTH_OPTIONS}
              />
            </DataViewFilters>
          }
          actions={
            <GatedButton
              variant="primary"
              onClick={() => setIsCreateOpen(true)}
              {...gateProps}
            >
              Nueva organización
            </GatedButton>
          }
          pagination={pager(PaginationVariant.top)}
        />
        <DataViewTable
          aria-label="Tabla de organizaciones del socio"
          columns={columns}
          rows={rows}
          bodyStates={bodyStates}
        />
        <DataViewToolbar pagination={pager(PaginationVariant.bottom)} />
      </DataView>
      <CreatePartnerTenantModal
        partnerId={partnerId}
        isOpen={isCreateOpen}
        onClose={() => setIsCreateOpen(false)}
      />
    </>
  );
};
