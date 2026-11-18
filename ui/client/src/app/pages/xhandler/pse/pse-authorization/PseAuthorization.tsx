import type React from "react";
import { useState } from "react";

import { Link } from "@tanstack/react-router";
import dayjs from "dayjs";

import {
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
  Flex,
  FlexItem,
  Label,
  PageSection,
  Stack,
  StackItem,
} from "@patternfly/react-core";
import { DataView, DataViewTable } from "@patternfly/react-data-view";

import type { AuthorizationView } from "@app/client/xhandler";
import { ConfirmActionModal } from "@app/components/ConfirmActionModal";
import { dataViewBodyStates } from "@app/components/dataViewStates";
import { DocumentTitle } from "@app/components/DocumentTitle";
import { GatedButton } from "@app/components/GatedButton";
import { TableEmptyState } from "@app/components/TableEmptyState";
import { TenantContextSelector } from "@app/components/TenantContextSelector";
import { RENDER_DATE_FORMAT, RENDER_DATETIME_FORMAT } from "@app/constants";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";
import { useTenantRole } from "@app/hooks/useTenantRole";
import { useSuspenseCompanyDetailQuery } from "@app/queries/xhandler/companies";
import {
  activeAuthorization,
  type EmissionModeValue,
  isEmissionMode,
  usePseAuthorizationsQuery,
  useRevokePseMutation,
  useSetEmissionModeMutation,
} from "@app/queries/xhandler/pse";
import { getMutationErrorMessage } from "@app/utils/apiError";
import type { DataViewTh } from "@app/utils/dataViewTypes";

import { RegisterPseAuthorizationModal } from "./components/RegisterPseAuthorizationModal";

interface IPseAuthorizationProps {
  companyId: string;
}

const EMISSION_MODE_LABELS: Record<EmissionModeValue, string> = {
  software: "Sistema del contribuyente (certificado propio)",
  pse: "PSE (firma la plataforma)",
};

const formatDate = (value: string | undefined): string =>
  value ? dayjs(value).format(RENDER_DATE_FORMAT) : "-";

const formatTime = (value: string | undefined): string =>
  value ? dayjs(value).format(RENDER_DATETIME_FORMAT) : "-";

interface IAuthorizationStateLabelProps {
  authorization: AuthorizationView;
}

const AuthorizationStateLabel: React.FC<IAuthorizationStateLabelProps> = ({
  authorization,
}) => {
  if (authorization.revoked_at) return <Label color="grey">Revocada</Label>;
  if (authorization.approved_at) return <Label color="green">Aprobada</Label>;
  return <Label color="orange">Pendiente de aprobación</Label>;
};

/**
 * PSE mode of a company (DESIGN.md §1.1, §3.2): the tenant registers its SOL authorization of the
 * platform with evidence, a platform admin approves it, then the company can switch to PSE mode.
 * Revoking it sends the company back to its own certificate.
 */
export const PseAuthorization: React.FC<IPseAuthorizationProps> = ({
  companyId,
}) => {
  const tenantId = useSelectedTenantId();
  const { addNotification } = useNotifications();
  const { can, disabledProps } = useTenantRole();
  const { data: company } = useSuspenseCompanyDetailQuery({ companyId });
  const { data, isLoading, error } = usePseAuthorizationsQuery({ companyId });
  const authorizations = data ?? [];
  const active = activeAuthorization(data);

  const [isRegisterOpen, setIsRegisterOpen] = useState(false);
  const [isRevokeOpen, setIsRevokeOpen] = useState(false);
  const [modeTarget, setModeTarget] = useState<EmissionModeValue | null>(null);

  const revoke = useRevokePseMutation(companyId, {
    onSuccess: () =>
      addNotification({
        variant: "success",
        title: "Se revocó la autorización de PSE",
        description:
          "La empresa vuelve a firmar con su certificado y enviar con su usuario SOL.",
      }),
    onError: (err) =>
      addNotification({
        variant: "danger",
        ...getMutationErrorMessage({
          error: err,
          fallback: "No se pudo revocar la autorización",
        }),
      }),
  });

  const setMode = useSetEmissionModeMutation(companyId, {
    onSuccess: (updated) =>
      addNotification({
        variant: "success",
        title:
          updated.emission_mode === "pse"
            ? "La empresa emite como PSE"
            : "La empresa emite con su propio certificado",
      }),
    onError: (err) =>
      addNotification({
        variant: "danger",
        ...getMutationErrorMessage({
          error: err,
          fallback: "No se pudo cambiar el modo de emisión",
        }),
      }),
  });

  const mode = company.emission_mode ?? "software";
  const otherMode: EmissionModeValue = mode === "pse" ? "software" : "pse";
  const title = company.razon_social || company.ruc || companyId;

  const adminGate = disabledProps("admin");
  const registerGate = !can("admin")
    ? adminGate
    : !company.ruc_verified_at
      ? {
          isAriaDisabled: true,
          tooltipProps: {
            content:
              "Verifica el RUC de la empresa antes de registrar la autorización.",
          },
        }
      : active
        ? {
            isAriaDisabled: true,
            tooltipProps: {
              content: "La empresa ya tiene una autorización vigente.",
            },
          }
        : {};
  const revokeGate = !can("admin")
    ? adminGate
    : !active
      ? {
          isAriaDisabled: true,
          tooltipProps: { content: "No hay una autorización vigente." },
        }
      : {};
  const modeGate = !can("admin")
    ? adminGate
    : otherMode === "pse" && !active?.approved_at
      ? {
          isAriaDisabled: true,
          tooltipProps: {
            content:
              "Requiere una autorización vigente y aprobada por la plataforma.",
          },
        }
      : {};

  const columns: DataViewTh[] = [
    { cell: "Autorizada en SOL", props: { width: 20 } },
    { cell: "Estado", props: { width: 20 } },
    { cell: "Registrada por", props: { width: 25 } },
    { cell: "Aprobada", props: { width: 15 } },
    { cell: "Revocada", props: { width: 20 } },
  ];
  // A handful per company, unpaged by the API: shown whole.
  const rows = authorizations.map((authorization) => ({
    id: authorization.id ?? "",
    row: [
      { cell: formatDate(authorization.authorized_at) },
      { cell: <AuthorizationStateLabel authorization={authorization} /> },
      {
        cell: authorization.registered_by ?? "-",
        props: { modifier: "truncate" as const },
      },
      { cell: formatTime(authorization.approved_at) },
      { cell: formatTime(authorization.revoked_at) },
    ],
  }));
  const { activeState, bodyStates } = dataViewBodyStates({
    columnCount: columns.length,
    isLoading,
    error,
    isEmpty: rows.length === 0,
    emptyState: (
      <TableEmptyState
        title="No hay autorizaciones registradas"
        body="Las autorizaciones de PSE que registres aparecerán aquí."
      />
    ),
  });

  return (
    <>
      <DocumentTitle title={`PSE - ${title}`} />
      <TenantContextSelector />
      <PageSection>
        <Breadcrumb>
          <BreadcrumbItem>
            <Link to="/organizaciones">Organizaciones</Link>
          </BreadcrumbItem>
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
          <BreadcrumbItem isActive>PSE</BreadcrumbItem>
        </Breadcrumb>
        <Content component={ContentVariants.h1}>Autorización de PSE</Content>
        <Content component={ContentVariants.p}>
          Como PSE, la plataforma firma con su certificado y envía a SUNAT con
          sus propios usuarios SOL en nombre de {title}.
        </Content>
      </PageSection>
      <PageSection>
        <Stack hasGutter>
          <StackItem>
            <Card>
              <CardTitle>Modo de emisión</CardTitle>
              <CardBody>
                <Flex
                  justifyContent={{ default: "justifyContentSpaceBetween" }}
                  alignItems={{ default: "alignItemsCenter" }}
                >
                  <FlexItem>
                    {isEmissionMode(mode) ? EMISSION_MODE_LABELS[mode] : mode}
                  </FlexItem>
                  <FlexItem>
                    <GatedButton
                      variant="secondary"
                      onClick={() => setModeTarget(otherMode)}
                      {...modeGate}
                    >
                      {otherMode === "pse"
                        ? "Emitir como PSE"
                        : "Emitir con certificado propio"}
                    </GatedButton>
                  </FlexItem>
                </Flex>
              </CardBody>
            </Card>
          </StackItem>
          <StackItem>
            <Card>
              <CardTitle>Autorización vigente</CardTitle>
              <CardBody>
                <Stack hasGutter>
                  <StackItem>
                    {active ? (
                      <DescriptionList
                        aria-label="Autorización vigente"
                        columnModifier={{ md: "2Col" }}
                      >
                        <DescriptionListGroup>
                          <DescriptionListTerm>
                            Autorizada en SOL
                          </DescriptionListTerm>
                          <DescriptionListDescription>
                            {formatDate(active.authorized_at)}
                          </DescriptionListDescription>
                        </DescriptionListGroup>
                        <DescriptionListGroup>
                          <DescriptionListTerm>Estado</DescriptionListTerm>
                          <DescriptionListDescription>
                            <AuthorizationStateLabel authorization={active} />
                          </DescriptionListDescription>
                        </DescriptionListGroup>
                        <DescriptionListGroup>
                          <DescriptionListTerm>
                            Registrada por
                          </DescriptionListTerm>
                          <DescriptionListDescription>
                            {active.registered_by ?? "-"}
                          </DescriptionListDescription>
                        </DescriptionListGroup>
                        <DescriptionListGroup>
                          <DescriptionListTerm>Constancia</DescriptionListTerm>
                          <DescriptionListDescription>
                            {active.evidence ? "Adjunta" : "Sin constancia"}
                          </DescriptionListDescription>
                        </DescriptionListGroup>
                      </DescriptionList>
                    ) : (
                      <Content component={ContentVariants.p}>
                        La empresa no tiene una autorización vigente.
                      </Content>
                    )}
                  </StackItem>
                  <StackItem>
                    <Flex>
                      <GatedButton
                        variant="primary"
                        onClick={() => setIsRegisterOpen(true)}
                        {...registerGate}
                      >
                        Registrar autorización
                      </GatedButton>
                      <GatedButton
                        variant="secondary"
                        onClick={() => setIsRevokeOpen(true)}
                        {...revokeGate}
                      >
                        Revocar autorización
                      </GatedButton>
                    </Flex>
                  </StackItem>
                </Stack>
              </CardBody>
            </Card>
          </StackItem>
          <StackItem>
            <Content component={ContentVariants.h2}>
              Historial de autorizaciones
            </Content>
            <DataView activeState={activeState}>
              <DataViewTable
                aria-label="Tabla de autorizaciones de PSE"
                columns={columns}
                rows={rows}
                bodyStates={bodyStates}
              />
            </DataView>
          </StackItem>
        </Stack>
      </PageSection>

      <RegisterPseAuthorizationModal
        companyId={companyId}
        isOpen={isRegisterOpen}
        onClose={() => setIsRegisterOpen(false)}
      />
      <ConfirmActionModal
        isOpen={isRevokeOpen}
        title="¿Revocar la autorización de PSE?"
        body="Hazlo cuando hayas revocado a la plataforma en SUNAT Operaciones en Línea. La empresa volverá a firmar con su propio certificado y enviar con su usuario SOL."
        confirmLabel="Revocar"
        cancelLabel="Volver"
        confirmVariant="primary"
        titleIconVariant="warning"
        isConfirming={revoke.isPending}
        onConfirm={() =>
          revoke.mutate(undefined, { onSuccess: () => setIsRevokeOpen(false) })
        }
        onCancel={() => setIsRevokeOpen(false)}
      />
      <ConfirmActionModal
        isOpen={modeTarget !== null}
        title="¿Cambiar el modo de emisión?"
        body={
          modeTarget === "pse"
            ? "Los próximos comprobantes los firmará la plataforma con su certificado de PSE y los enviará con sus usuarios SOL."
            : "Los próximos comprobantes se firmarán con el certificado de la empresa y se enviarán con su usuario SOL."
        }
        confirmLabel="Cambiar modo"
        cancelLabel="Volver"
        confirmVariant="primary"
        isConfirming={setMode.isPending}
        onConfirm={() =>
          modeTarget &&
          setMode.mutate(
            { emission_mode: modeTarget },
            { onSuccess: () => setModeTarget(null) },
          )
        }
        onCancel={() => setModeTarget(null)}
      />
    </>
  );
};
