import type React from "react";
import { useState } from "react";

import { Link } from "@tanstack/react-router";
import dayjs from "dayjs";

import {
  ActionList,
  ActionListGroup,
  ActionListItem,
  Breadcrumb,
  BreadcrumbItem,
  Card,
  CardBody,
  CardTitle,
  Content,
  ContentVariants,
  DescriptionList,
  DescriptionListDescription,
  DescriptionListGroup,
  DescriptionListTerm,
  Dropdown,
  DropdownItem,
  DropdownList,
  Flex,
  FlexItem,
  Grid,
  GridItem,
  List,
  ListItem,
  MenuToggle,
  PageSection,
} from "@patternfly/react-core";
import EllipsisVIcon from "@patternfly/react-icons/dist/esm/icons/ellipsis-v-icon";

import { ConfirmActionModal } from "@app/components/ConfirmActionModal";
import { DocumentStatusLabel } from "@app/components/DocumentStatusLabel";
import { DocumentTitle } from "@app/components/DocumentTitle";
import { GatedButton } from "@app/components/GatedButton";
import { SunatEnvLabel } from "@app/components/SunatEnvLabel";
import {
  DEFAULT_REFETCH_INTERVAL,
  RENDER_DATE_FORMAT,
  RENDER_DATETIME_FORMAT,
} from "@app/constants";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";
import { useTenantRole } from "@app/hooks/useTenantRole";
import {
  type DocumentFileKind,
  isDocumentSettling,
  useDocumentAccessLinkCreateMutation,
  useDocumentAccessLinksRevokeMutation,
  useDocumentCancelVoidMutation,
  useDocumentDetailQuery,
  useDocumentEventsQuery,
  useDocumentFileDownloadMutation,
  useDocumentRetryMutation,
  useSuspenseDocumentDetailQuery,
} from "@app/queries/xhandler/documents";
import { getMutationErrorMessage } from "@app/utils/apiError";
import {
  formatTotal,
  tipoComprobanteLabel,
} from "@app/utils/documentPresentation";

import { AccessLinkModal } from "./components/AccessLinkModal";
import { DocumentTimeline } from "./components/DocumentTimeline";
import { EmailDocumentModal } from "./components/EmailDocumentModal";
import { VoidDocumentModal } from "./components/VoidDocumentModal";
import { documentActionAvailability } from "./documentActions";

interface IDocumentDetailProps {
  documentId: string;
}

type OpenDialog = "void" | "email" | "cancel-void" | "revoke-links" | null;

const formatTime = (value: string | undefined): string =>
  value ? dayjs(value).format(RENDER_DATETIME_FORMAT) : "-";

const FILE_LABELS: Record<DocumentFileKind, string> = {
  xml: "XML firmado",
  cdr: "CDR de SUNAT",
  pdf: "PDF",
};

/** One issued document: its SUNAT status, timeline, files and the actions on it (DESIGN.md §9, §11). */
export const DocumentDetail: React.FC<IDocumentDetailProps> = ({
  documentId,
}) => {
  const tenantId = useSelectedTenantId();
  const { addNotification } = useNotifications();
  const { can, disabledProps } = useTenantRole();
  const [openDialog, setOpenDialog] = useState<OpenDialog>(null);
  const [isKebabOpen, setIsKebabOpen] = useState(false);
  const [accessUrl, setAccessUrl] = useState<string | null>(null);

  // Server-derived status: refreshed while SUNAT may still change it (ADR 0007).
  const refetchWhileSettling = {
    refetchInterval: (query: { state: { data?: { status?: string } } }) =>
      isDocumentSettling(query.state.data?.status)
        ? DEFAULT_REFETCH_INTERVAL
        : false,
  };
  const { data: document } = useSuspenseDocumentDetailQuery({
    documentId,
    options: refetchWhileSettling,
  });
  const isSettling = isDocumentSettling(document.status);
  // GET …/events is unpaged (one row per status change): read whole.
  const events = useDocumentEventsQuery({
    documentId,
    options: {
      refetchInterval: isSettling ? DEFAULT_REFETCH_INTERVAL : false,
    },
  });
  const { data: voidingDocument } = useDocumentDetailQuery({
    documentId: document.voided_by_document_id,
    options: {
      refetchInterval: isSettling ? DEFAULT_REFETCH_INTERVAL : false,
    },
  });
  const availability = documentActionAvailability({
    document,
    voidingDocument,
  });
  const title =
    `${tipoComprobanteLabel(document.tipo_comprobante)} ${document.serie_numero ?? ""}`.trim();

  const errorToast = (fallback: string) => (error: unknown) =>
    addNotification({
      variant: "danger",
      ...getMutationErrorMessage({ error, fallback }),
    });

  const retry = useDocumentRetryMutation({
    onSuccess: (data) =>
      addNotification({
        variant: "info",
        title: `Se reenviará ${data.serie_numero ?? "el comprobante"} a SUNAT`,
        description: "Su estado se actualiza solo cuando SUNAT responda.",
      }),
    onError: errorToast("No se pudo reenviar el comprobante"),
  });
  const cancelVoid = useDocumentCancelVoidMutation({
    onSuccess: (data) =>
      addNotification({
        variant: "success",
        title: `Se canceló la anulación de ${data.serie_numero ?? "el comprobante"}`,
      }),
    onError: errorToast("No se pudo cancelar la anulación"),
  });
  const createLink = useDocumentAccessLinkCreateMutation({
    onError: errorToast("No se pudo crear el enlace de acceso"),
  });
  const revokeLinks = useDocumentAccessLinksRevokeMutation({
    onSuccess: (_data, revoked) =>
      addNotification({
        variant: "success",
        title: `Se revocaron los enlaces de ${revoked.serie_numero ?? "el comprobante"}`,
      }),
    onError: errorToast("No se pudieron revocar los enlaces de acceso"),
  });
  const download = useDocumentFileDownloadMutation({
    onError: errorToast("No se pudo descargar el archivo"),
  });

  /** Disabled with the reason: the role first, then the document's state. */
  const gate = (reason: string | undefined) =>
    !can("operator")
      ? disabledProps("operator")
      : reason
        ? { isAriaDisabled: true, tooltipProps: { content: reason } }
        : {};

  const kebabItems = [
    {
      key: "cancel-void",
      title: "Cancelar anulación",
      onClick: () => setOpenDialog("cancel-void"),
      ...gate(availability.cancelVoid),
    },
    {
      key: "create-link",
      title: "Crear enlace de acceso",
      onClick: () =>
        createLink.mutate(document, {
          onSuccess: (link) => setAccessUrl(link.access_url ?? null),
        }),
      ...gate(undefined),
    },
    {
      key: "revoke-links",
      title: "Revocar enlaces de acceso",
      onClick: () => setOpenDialog("revoke-links"),
      ...gate(undefined),
    },
  ];

  const sunatFields: { label: string; value: React.ReactNode }[] = [
    {
      label: "Estado",
      value: (
        <DocumentStatusLabel
          status={document.status}
          holdReason={document.hold_reason}
        />
      ),
    },
    {
      label: "Código SUNAT",
      value:
        document.sunat_code !== undefined && document.sunat_code !== null
          ? String(document.sunat_code)
          : "-",
    },
    { label: "Descripción SUNAT", value: document.sunat_description || "-" },
    {
      label: "Observaciones",
      value: document.sunat_notes?.length ? (
        <List isPlain>
          {document.sunat_notes.map((note) => (
            <ListItem key={note}>{note}</ListItem>
          ))}
        </List>
      ) : (
        "-"
      ),
    },
    { label: "Intentos de envío", value: String(document.attempts ?? 0) },
    { label: "Primer envío", value: formatTime(document.first_sent_at) },
    { label: "Próximo intento", value: formatTime(document.next_attempt_at) },
    { label: "CDR recibido", value: formatTime(document.cdr_received_at) },
    { label: "Plazo legal", value: formatTime(document.deadline_at) },
    {
      label: "Entregado al comprador",
      value: document.delivered_at
        ? `${formatTime(document.delivered_at)}${document.delivered_source === "client" ? " (declarado por el emisor)" : ""}`
        : "No",
    },
  ];

  const documentFields: { label: string; value: React.ReactNode }[] = [
    { label: "Tipo", value: tipoComprobanteLabel(document.tipo_comprobante) },
    { label: "Serie y número", value: document.serie_numero ?? "-" },
    {
      label: "Fecha de emisión",
      value: document.fecha_emision
        ? dayjs(document.fecha_emision).format(RENDER_DATE_FORMAT)
        : "-",
    },
    {
      label: "Cliente",
      value: document.customer
        ? [document.customer.nombre, document.customer.numero]
            .filter(Boolean)
            .join(" · ") || "-"
        : "-",
    },
    {
      label: "Total",
      value: formatTotal({ total: document.total, moneda: document.moneda }),
    },
    {
      label: "Ambiente SUNAT",
      value: <SunatEnvLabel sunatEnv={document.sunat_env} />,
    },
    {
      label: "Empresa",
      value: document.company_id ? (
        <Link
          to="/organizaciones/$tenantId/empresas/$companyId"
          params={{ tenantId, companyId: document.company_id }}
        >
          Ver empresa
        </Link>
      ) : (
        "-"
      ),
    },
    { label: "Registrado", value: formatTime(document.created_at) },
  ];

  const related: { label: string; id: string | undefined }[] = [
    { label: "Documento que lo anula", id: document.voided_by_document_id },
    { label: "Documento afectado", id: document.affected_document_id },
    { label: "Resumen diario", id: document.summary_document_id },
  ];
  for (const { label, id } of related) {
    if (id) {
      documentFields.push({
        label,
        value: (
          <Link
            to="/organizaciones/$tenantId/documentos/$documentId"
            params={{ tenantId, documentId: id }}
          >
            {label === "Documento que lo anula" && voidingDocument?.serie_numero
              ? voidingDocument.serie_numero
              : "Ver documento"}
          </Link>
        ),
      });
    }
  }

  const descriptionList = (
    ariaLabel: string,
    fields: { label: string; value: React.ReactNode }[],
  ) => (
    <DescriptionList aria-label={ariaLabel} isCompact isHorizontal>
      {fields.map(({ label, value }) => (
        <DescriptionListGroup key={label}>
          <DescriptionListTerm>{label}</DescriptionListTerm>
          <DescriptionListDescription>{value}</DescriptionListDescription>
        </DescriptionListGroup>
      ))}
    </DescriptionList>
  );

  return (
    <>
      <DocumentTitle title={title} />
      <PageSection>
        <Breadcrumb>
          <BreadcrumbItem>
            <Link to="/organizaciones">Organizaciones</Link>
          </BreadcrumbItem>
          <BreadcrumbItem>
            <Link
              to="/organizaciones/$tenantId/documentos"
              params={{ tenantId }}
            >
              Comprobantes
            </Link>
          </BreadcrumbItem>
          <BreadcrumbItem isActive>{document.serie_numero}</BreadcrumbItem>
        </Breadcrumb>
        <Flex
          justifyContent={{ default: "justifyContentSpaceBetween" }}
          alignItems={{ default: "alignItemsCenter" }}
        >
          <FlexItem>
            <Content component={ContentVariants.h1}>{title}</Content>
          </FlexItem>
          <FlexItem>
            <ActionList>
              <ActionListGroup>
                <ActionListItem>
                  <GatedButton
                    variant="primary"
                    isLoading={retry.isPending}
                    onClick={() => retry.mutate(document)}
                    {...gate(availability.retry)}
                  >
                    Reenviar
                  </GatedButton>
                </ActionListItem>
                <ActionListItem>
                  <GatedButton
                    variant="secondary"
                    onClick={() => setOpenDialog("void")}
                    {...gate(availability.void)}
                  >
                    Anular
                  </GatedButton>
                </ActionListItem>
                <ActionListItem>
                  <GatedButton
                    variant="secondary"
                    onClick={() => setOpenDialog("email")}
                    {...gate(availability.files)}
                  >
                    Enviar por correo
                  </GatedButton>
                </ActionListItem>
                <ActionListItem>
                  <Dropdown
                    isOpen={isKebabOpen}
                    onOpenChange={setIsKebabOpen}
                    onSelect={() => setIsKebabOpen(false)}
                    popperProps={{ position: "right" }}
                    toggle={(toggleRef) => (
                      <MenuToggle
                        ref={toggleRef}
                        variant="plain"
                        aria-label="Más acciones"
                        isExpanded={isKebabOpen}
                        onClick={() => setIsKebabOpen((open) => !open)}
                        icon={<EllipsisVIcon />}
                      />
                    )}
                  >
                    <DropdownList>
                      {kebabItems.map(({ key, title: itemTitle, ...props }) => (
                        <DropdownItem key={key} {...props}>
                          {itemTitle}
                        </DropdownItem>
                      ))}
                    </DropdownList>
                  </Dropdown>
                </ActionListItem>
              </ActionListGroup>
            </ActionList>
          </FlexItem>
        </Flex>
      </PageSection>
      <PageSection>
        <Grid hasGutter>
          <GridItem md={6}>
            <Card isFullHeight>
              <CardTitle>Estado en SUNAT</CardTitle>
              <CardBody>
                {descriptionList("Estado en SUNAT", sunatFields)}
              </CardBody>
            </Card>
          </GridItem>
          <GridItem md={6}>
            <Card isFullHeight>
              <CardTitle>Datos del comprobante</CardTitle>
              <CardBody>
                {descriptionList("Datos del comprobante", documentFields)}
              </CardBody>
            </Card>
          </GridItem>
          <GridItem md={6}>
            <Card isFullHeight>
              <CardTitle>Archivos</CardTitle>
              <CardBody>
                <ActionList>
                  {(Object.keys(FILE_LABELS) as DocumentFileKind[]).map(
                    (kind) => {
                      const reason =
                        availability.files ??
                        (kind === "cdr" ? availability.cdr : undefined);
                      return (
                        <ActionListItem key={kind}>
                          <GatedButton
                            variant="secondary"
                            isLoading={
                              download.isPending &&
                              download.variables.kind === kind
                            }
                            onClick={() => download.mutate({ document, kind })}
                            {...(reason
                              ? {
                                  isAriaDisabled: true,
                                  tooltipProps: { content: reason },
                                }
                              : {})}
                          >
                            Descargar {FILE_LABELS[kind]}
                          </GatedButton>
                        </ActionListItem>
                      );
                    },
                  )}
                </ActionList>
              </CardBody>
            </Card>
          </GridItem>
          <GridItem md={6}>
            <Card isFullHeight>
              <CardTitle>Línea de tiempo</CardTitle>
              <CardBody>
                {events.error ? (
                  <Content component={ContentVariants.p}>
                    No se pudo cargar la línea de tiempo.
                  </Content>
                ) : (
                  <DocumentTimeline events={events.data ?? []} />
                )}
              </CardBody>
            </Card>
          </GridItem>
        </Grid>
      </PageSection>

      {openDialog === "void" ? (
        <VoidDocumentModal
          document={document}
          onClose={() => setOpenDialog(null)}
        />
      ) : null}
      {openDialog === "email" ? (
        <EmailDocumentModal
          document={document}
          onClose={() => setOpenDialog(null)}
        />
      ) : null}
      {accessUrl ? (
        <AccessLinkModal
          accessUrl={accessUrl}
          onClose={() => setAccessUrl(null)}
        />
      ) : null}
      <ConfirmActionModal
        isOpen={openDialog === "cancel-void"}
        title="¿Cancelar la anulación?"
        body={`${document.serie_numero ?? "El comprobante"} vuelve a su estado anterior y su comunicación de baja con error se descarta.`}
        confirmLabel="Cancelar anulación"
        cancelLabel="Volver"
        confirmVariant="primary"
        titleIconVariant="warning"
        isConfirming={cancelVoid.isPending}
        onConfirm={() =>
          cancelVoid.mutate(document, {
            onSuccess: () => setOpenDialog(null),
          })
        }
        onCancel={() => setOpenDialog(null)}
      />
      <ConfirmActionModal
        isOpen={openDialog === "revoke-links"}
        title="¿Revocar los enlaces de acceso?"
        body="Todos los enlaces del comprador para este comprobante dejan de funcionar ahora. Puedes crear uno nuevo después."
        confirmLabel="Revocar enlaces"
        cancelLabel="Volver"
        confirmVariant="primary"
        titleIconVariant="warning"
        isConfirming={revokeLinks.isPending}
        onConfirm={() =>
          revokeLinks.mutate(document, {
            onSuccess: () => setOpenDialog(null),
          })
        }
        onCancel={() => setOpenDialog(null)}
      />
    </>
  );
};
