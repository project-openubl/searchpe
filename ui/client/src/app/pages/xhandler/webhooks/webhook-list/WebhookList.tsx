import type React from "react";
import { useState } from "react";

import { Link, useNavigate } from "@tanstack/react-router";
import dayjs from "dayjs";

import {
  Content,
  ContentVariants,
  Flex,
  FlexItem,
  Label,
  PageSection,
} from "@patternfly/react-core";
import { DataView, DataViewTable } from "@patternfly/react-data-view";
import { ActionsColumn, type IAction } from "@patternfly/react-table";

import type { WebhookView } from "@app/client/xhandler";
import { ConfirmActionModal } from "@app/components/ConfirmActionModal";
import { dataViewBodyStates } from "@app/components/dataViewStates";
import { DocumentTitle } from "@app/components/DocumentTitle";
import { GatedButton } from "@app/components/GatedButton";
import { TableEmptyState } from "@app/components/TableEmptyState";
import { TenantContextSelector } from "@app/components/TenantContextSelector";
import { RENDER_DATETIME_FORMAT } from "@app/constants";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";
import { useTenantRole } from "@app/hooks/useTenantRole";
import {
  useWebhookDeleteMutation,
  useWebhooksListQuery,
  useWebhookTestMutation,
} from "@app/queries/xhandler/webhooks";
import { getMutationErrorMessage } from "@app/utils/apiError";
import type { DataViewTh } from "@app/utils/dataViewTypes";

import { CreateWebhookModal } from "./components/CreateWebhookModal";
import { webhookEventLabel } from "./utils/webhookEvents";

/**
 * The organization's webhook subscriptions (DESIGN.md §12). The API answers them all at once
 * (a tenant has a few), so the table doesn't page.
 */
export const WebhookList: React.FC = () => {
  const tenantId = useSelectedTenantId();
  const navigate = useNavigate();
  const { addNotification } = useNotifications();
  const { can, disabledProps } = useTenantRole();
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<WebhookView | null>(null);
  const { data, isLoading, error } = useWebhooksListQuery();

  const deleteWebhook = useWebhookDeleteMutation({
    onSuccess: (_data, webhook) =>
      addNotification({
        variant: "success",
        title: `Se eliminó el webhook ${webhook.url ?? ""}`.trim(),
      }),
    onError: (err) =>
      addNotification({
        variant: "danger",
        ...getMutationErrorMessage({
          error: err,
          fallback: "No se pudo eliminar el webhook",
        }),
      }),
  });

  const testWebhook = useWebhookTestMutation({
    onSuccess: (_delivery, webhook) =>
      addNotification({
        variant: "info",
        title: `Se envió un evento de prueba a ${webhook.url ?? "el webhook"}`,
      }),
    onError: (err) =>
      addNotification({
        variant: "danger",
        ...getMutationErrorMessage({
          error: err,
          fallback: "No se pudo enviar el evento de prueba",
        }),
      }),
  });

  const adminGate = disabledProps("admin").tooltipProps;
  const actionsFor = (webhook: WebhookView): IAction[] => [
    {
      title: "Ver entregas",
      onClick: () =>
        webhook.id &&
        navigate({
          to: "/organizaciones/$tenantId/webhooks/$webhookId",
          params: { tenantId, webhookId: webhook.id },
        }),
    },
    {
      title: "Enviar evento de prueba",
      onClick: () => testWebhook.mutate(webhook),
      isAriaDisabled: !can("admin"),
      tooltipProps: adminGate,
    },
    {
      title: "Eliminar",
      onClick: () => setDeleteTarget(webhook),
      isAriaDisabled: !can("admin"),
      tooltipProps: adminGate,
    },
  ];

  const columns: DataViewTh[] = [
    { cell: "URL", props: { width: 40 } },
    { cell: "Eventos", props: { width: 30 } },
    { cell: "Estado", props: { width: 10 } },
    { cell: "Creado", props: { width: 20 } },
    { cell: "" },
  ];

  const rows = (data ?? []).map((webhook) => ({
    id: webhook.id ?? "",
    row: [
      {
        cell: webhook.id ? (
          <Link
            to="/organizaciones/$tenantId/webhooks/$webhookId"
            params={{ tenantId, webhookId: webhook.id }}
          >
            {webhook.url}
          </Link>
        ) : (
          webhook.url
        ),
        props: { modifier: "truncate" as const },
      },
      {
        cell: (webhook.events ?? []).map(webhookEventLabel).join(", ") || "-",
        props: { modifier: "truncate" as const },
      },
      {
        cell: webhook.active ? (
          <Label color="green" isCompact>
            Activo
          </Label>
        ) : (
          <Label isCompact>Inactivo</Label>
        ),
      },
      {
        cell: webhook.created_at
          ? dayjs(webhook.created_at).format(RENDER_DATETIME_FORMAT)
          : "-",
      },
      {
        cell: (
          <ActionsColumn
            items={actionsFor(webhook)}
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
        title="No hay webhooks"
        body="Los webhooks avisan a tu sistema cuando cambia el estado de un comprobante."
      />
    ),
  });

  return (
    <>
      <DocumentTitle title="Webhooks" />
      <TenantContextSelector />
      <PageSection>
        <Flex
          justifyContent={{ default: "justifyContentSpaceBetween" }}
          alignItems={{ default: "alignItemsCenter" }}
        >
          <FlexItem>
            <Content component={ContentVariants.h1}>Webhooks</Content>
            <Content component={ContentVariants.p}>
              Las URL de tus sistemas que reciben los eventos de esta
              organización, firmados con HMAC-SHA256.
            </Content>
          </FlexItem>
          <FlexItem>
            <GatedButton
              variant="primary"
              onClick={() => setIsCreateOpen(true)}
              {...disabledProps("admin")}
            >
              Nuevo webhook
            </GatedButton>
          </FlexItem>
        </Flex>
      </PageSection>
      <PageSection>
        <DataView activeState={activeState}>
          <DataViewTable
            aria-label="Tabla de webhooks"
            columns={columns}
            rows={rows}
            bodyStates={bodyStates}
          />
        </DataView>
      </PageSection>

      <CreateWebhookModal
        isOpen={isCreateOpen}
        onClose={() => setIsCreateOpen(false)}
      />
      <ConfirmActionModal
        isOpen={deleteTarget !== null}
        title="¿Eliminar el webhook?"
        body={`${deleteTarget?.url ?? "El webhook"} dejará de recibir eventos. No se puede deshacer.`}
        confirmLabel="Eliminar"
        isConfirming={deleteWebhook.isPending}
        onConfirm={() =>
          deleteTarget &&
          deleteWebhook.mutate(deleteTarget, {
            onSuccess: () => setDeleteTarget(null),
          })
        }
        onCancel={() => setDeleteTarget(null)}
      />
    </>
  );
};
