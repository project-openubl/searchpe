import type React from "react";
import { useState } from "react";

import dayjs from "dayjs";

import {
  Content,
  ContentVariants,
  Label,
  PageSection,
  PaginationVariant,
  ToolbarItem,
} from "@patternfly/react-core";
import {
  DataView,
  DataViewTable,
  DataViewToolbar,
} from "@patternfly/react-data-view";
import { ActionsColumn, type IAction } from "@patternfly/react-table";

import { dataViewBodyStates } from "@app/components/dataViewStates";
import { DocumentTitle } from "@app/components/DocumentTitle";
import { GatedButton } from "@app/components/GatedButton";
import { ListPagination } from "@app/components/ListPagination";
import { TableEmptyState } from "@app/components/TableEmptyState";
import { TenantContextSelector } from "@app/components/TenantContextSelector";
import { UnauthorizedState } from "@app/components/UnauthorizedState";
import { RENDER_DATE_FORMAT, RENDER_DATETIME_FORMAT } from "@app/constants";
import { usePersistentState } from "@app/hooks/usePersistentState";
import { useTenantRole } from "@app/hooks/useTenantRole";
import {
  type IDataSubjectRequestRow,
  isDataSubjectRequestKind,
  isDataSubjectRequestStatus,
  toDataSubjectRequestRow,
  useDataSubjectRequestsListQuery,
} from "@app/queries/xhandler/data-subject-requests";
import type { DataViewTh } from "@app/utils/dataViewTypes";
import { PAGINATION_LIST_STATE, toPageParams } from "@app/utils/listState";

import { CloseDataSubjectRequestModal } from "./components/CloseDataSubjectRequestModal";
import { CreateDataSubjectRequestModal } from "./components/CreateDataSubjectRequestModal";
import { PersonalDataModal } from "./components/PersonalDataModal";
import {
  DATA_SUBJECT_REQUEST_KIND_LABELS,
  DATA_SUBJECT_REQUEST_STATUS_LABELS,
  isOverdue,
  subjectDocTypeLabel,
} from "./dataSubjectRequestPresentation";

type OpenDialog =
  | { kind: "create" }
  | { kind: "personal-data"; request: IDataSubjectRequestRow }
  | { kind: "close"; request: IDataSubjectRequestRow }
  | null;

const formatTime = (value: string): string =>
  value ? dayjs(value).format(RENDER_DATETIME_FORMAT) : "-";

/** Personal-data (ARCO) requests the tenant received, with their legal terms (Ley 29733). */
export const DataSubjectRequestList: React.FC = () => {
  const { can } = useTenantRole();
  const [openDialog, setOpenDialog] = useState<OpenDialog>(null);
  // "Overdue" is judged against when the page opened; render stays pure.
  const [now] = useState(() => new Date());
  const [pagination, setPagination] = usePersistentState({
    ...PAGINATION_LIST_STATE.pagination,
    storage: "url",
  });

  const isAdmin = can("admin");
  // GET /data-subject-requests is unpaged (the latest 200): read whole, paged on screen.
  const { data, isLoading, error } = useDataSubjectRequestsListQuery({
    options: { enabled: isAdmin },
  });
  const requests = (data ?? []).map(toDataSubjectRequestRow);
  const { limit, offset } = toPageParams(pagination);
  const pageRequests = requests.slice(offset, offset + limit);

  const actionsFor = (request: IDataSubjectRequestRow): IAction[] => [
    {
      title: "Ver datos personales",
      onClick: () => setOpenDialog({ kind: "personal-data", request }),
    },
    {
      title: "Cerrar",
      onClick: () => setOpenDialog({ kind: "close", request }),
      isAriaDisabled: request.status !== "open",
      tooltipProps:
        request.status !== "open"
          ? { content: "La solicitud ya está cerrada." }
          : undefined,
    },
  ];

  const columns: DataViewTh[] = [
    { cell: "Recibida", props: { width: 15 } },
    { cell: "Titular", props: { width: 25 } },
    { cell: "Derecho", props: { width: 15 } },
    { cell: "Vence", props: { width: 15 } },
    { cell: "Estado", props: { width: 10 } },
    { cell: "Respuesta", props: { width: 20 } },
    { cell: "" },
  ];

  const rows = pageRequests.map((request) => ({
    id: request.id,
    row: [
      { cell: formatTime(request.receivedAt) },
      {
        cell: `${request.subjectName ? `${request.subjectName} · ` : ""}${subjectDocTypeLabel(request.subjectDocType)} ${request.subjectDocNumber}`,
        props: { modifier: "truncate" as const },
      },
      {
        cell: isDataSubjectRequestKind(request.kind)
          ? DATA_SUBJECT_REQUEST_KIND_LABELS[request.kind]
          : request.kind,
      },
      {
        cell: isOverdue({
          status: request.status,
          responseDueAt: request.responseDueAt,
          now,
        }) ? (
          <Label color="red" isCompact>
            Vencida el {dayjs(request.responseDueAt).format(RENDER_DATE_FORMAT)}
          </Label>
        ) : request.responseDueAt ? (
          dayjs(request.responseDueAt).format(RENDER_DATE_FORMAT)
        ) : (
          "-"
        ),
      },
      {
        cell: (
          <Label color={request.status === "open" ? "blue" : "grey"} isCompact>
            {isDataSubjectRequestStatus(request.status)
              ? DATA_SUBJECT_REQUEST_STATUS_LABELS[request.status]
              : request.status}
          </Label>
        ),
      },
      {
        cell: request.resolution || "-",
        props: { modifier: "truncate" as const },
      },
      {
        cell: (
          <ActionsColumn
            items={actionsFor(request)}
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
    emptyState: (
      <TableEmptyState
        title="No hay solicitudes"
        body="Registra aquí las solicitudes de acceso, rectificación, cancelación u oposición que reciba la organización."
      />
    ),
  });

  const pager = (variant: PaginationVariant) => (
    <ListPagination
      variant={variant}
      itemCount={requests.length}
      pagination={pagination}
      onPaginationChange={setPagination}
    />
  );

  return (
    <>
      <DocumentTitle title="Datos personales" />
      <TenantContextSelector />
      <PageSection>
        <Content component={ContentVariants.h1}>
          Solicitudes de datos personales
        </Content>
        <Content component={ContentVariants.p}>
          Solicitudes de los compradores sobre sus datos personales (Ley 29733)
          y el plazo para responderlas.
        </Content>
      </PageSection>
      <PageSection>
        {isAdmin ? (
          <DataView activeState={activeState}>
            <DataViewToolbar
              actions={
                <ToolbarItem>
                  <GatedButton
                    variant="primary"
                    onClick={() => setOpenDialog({ kind: "create" })}
                  >
                    Nueva solicitud
                  </GatedButton>
                </ToolbarItem>
              }
              pagination={pager(PaginationVariant.top)}
            />
            <DataViewTable
              aria-label="Tabla de solicitudes de datos personales"
              columns={columns}
              rows={rows}
              bodyStates={bodyStates}
            />
            <DataViewToolbar pagination={pager(PaginationVariant.bottom)} />
          </DataView>
        ) : (
          <UnauthorizedState description="Necesitas el rol Administrador o superior para atender solicitudes de datos personales." />
        )}
      </PageSection>

      {openDialog?.kind === "create" ? (
        <CreateDataSubjectRequestModal onClose={() => setOpenDialog(null)} />
      ) : null}
      {openDialog?.kind === "personal-data" ? (
        <PersonalDataModal
          request={openDialog.request}
          onClose={() => setOpenDialog(null)}
        />
      ) : null}
      {openDialog?.kind === "close" ? (
        <CloseDataSubjectRequestModal
          request={openDialog.request}
          onClose={() => setOpenDialog(null)}
        />
      ) : null}
    </>
  );
};
