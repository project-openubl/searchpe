import type React from "react";
import { useState } from "react";

import { Link } from "@tanstack/react-router";
import dayjs from "dayjs";

import {
  Alert,
  Breadcrumb,
  BreadcrumbItem,
  Bullseye,
  Card,
  CardBody,
  CardTitle,
  Content,
  ContentVariants,
  DescriptionList,
  DescriptionListDescription,
  DescriptionListGroup,
  DescriptionListTerm,
  EmptyState,
  EmptyStateBody,
  FormSelect,
  FormSelectOption,
  PageSection,
  Spinner,
  Stack,
  StackItem,
  ToggleGroup,
  ToggleGroupItem,
  Toolbar,
  ToolbarContent,
  ToolbarGroup,
  ToolbarItem,
} from "@patternfly/react-core";
import CubesIcon from "@patternfly/react-icons/dist/esm/icons/cubes-icon";

import type { SirePeriodView } from "@app/client/xhandler";
import { ApiErrorText } from "@app/components/ApiErrorText";
import { ConfirmActionModal } from "@app/components/ConfirmActionModal";
import { DocumentTitle } from "@app/components/DocumentTitle";
import { GatedButton } from "@app/components/GatedButton";
import { SunatEnvLabel } from "@app/components/SunatEnvLabel";
import { TenantContextSelector } from "@app/components/TenantContextSelector";
import {
  DEFAULT_REFETCH_INTERVAL,
  RENDER_DATETIME_FORMAT,
} from "@app/constants";
import { useNotifications } from "@app/hooks/useNotifications";
import { usePersistentState } from "@app/hooks/usePersistentState";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";
import { useTenantRole } from "@app/hooks/useTenantRole";
import { useSuspenseCompanyDetailQuery } from "@app/queries/xhandler/companies";
import {
  type ISirePeriodPath,
  isSireReplaceable,
  isSireStatusInProgress,
  SIRE_BOOKS,
  type SireBook,
  type SireFileKind,
  sireReconciliation,
  useDownloadSireFileMutation,
  useRequestSireProposalMutation,
  useSirePeriodQuery,
} from "@app/queries/xhandler/sire";
import { getMutationErrorMessage } from "@app/utils/apiError";
import { isNotFoundError } from "@app/utils/isHttpError";

import { SireReconciliation } from "./components/SireReconciliation";
import { SireReplaceModal } from "./components/SireReplaceModal";
import { SireStatusLabel } from "./components/SireStatusLabel";
import { SIRE_PERIOD_STATE, sirePeriods } from "./sirePeriodState";

interface ISirePeriodProps {
  companyId: string;
}

const BOOK_LABELS: Record<SireBook, string> = {
  rvie: "Ventas (RVIE)",
  rce: "Compras (RCE)",
};

const PERIOD_OPTIONS = sirePeriods().map((period) => ({
  value: period,
  label: dayjs(`${period.slice(0, 4)}-${period.slice(4)}-01`).format(
    "MMMM [de] YYYY",
  ),
}));

const FILE_LABELS: Record<SireFileKind, string> = {
  proposal: "la propuesta",
  replacement: "el reemplazo",
};

/** States whose re-request discards a reviewed proposal or SUNAT's acceptance (admins only). */
const isStartOver = (period: SirePeriodView | undefined): boolean =>
  period?.status === "proposal_ready" || period?.status === "accepted";

/** Why a proposal can't be requested now, or undefined when it can. */
const requestBlocker = (
  period: SirePeriodView | undefined,
): string | undefined => {
  if (isSireStatusInProgress(period?.status)) {
    return "SUNAT aún está atendiendo este periodo; espera su resultado.";
  }
  if (period?.status === "unknown") {
    return "No se sabe si SUNAT recibió el último reemplazo: reemplaza confirmando que no lo recibió.";
  }
  return undefined;
};

/**
 * SIRE per company, register and period (DESIGN.md §11.7): request SUNAT's proposal, review its
 * reconciliation with the stored documents, submit the replacement and download SUNAT's files.
 * SUNAT is called by jobs, so the period refreshes while one is running.
 */
export const SirePeriod: React.FC<ISirePeriodProps> = ({ companyId }) => {
  const tenantId = useSelectedTenantId();
  const { addNotification } = useNotifications();
  const { can, disabledProps } = useTenantRole();
  const { data: company } = useSuspenseCompanyDetailQuery({ companyId });

  const [selection, setSelection] = usePersistentState({
    ...SIRE_PERIOD_STATE.period,
    storage: "url",
  });
  const path: ISirePeriodPath = {
    companyId,
    book: selection.libro,
    period: selection.periodo,
  };

  const { data, isLoading, error } = useSirePeriodQuery({
    path,
    options: {
      // Server-derived state: poll while a job or SUNAT still moves it on.
      refetchInterval: (query) =>
        isSireStatusInProgress(query.state.data?.status)
          ? DEFAULT_REFETCH_INTERVAL
          : false,
    },
  });
  const isNotRequested = isNotFoundError(error);
  const period = error ? undefined : data;

  const [isStartOverOpen, setIsStartOverOpen] = useState(false);
  const [replaceTarget, setReplaceTarget] = useState<SirePeriodView | null>(
    null,
  );

  const requestProposal = useRequestSireProposalMutation({
    onSuccess: (_period, variables) =>
      addNotification({
        variant: "info",
        title: `Se solicitó a SUNAT la propuesta ${variables.book.toUpperCase()} ${variables.period}`,
        description: "Un trabajo la traerá y la conciliará.",
      }),
    onError: (err) =>
      addNotification({
        variant: "danger",
        ...getMutationErrorMessage({
          error: err,
          fallback: "No se pudo solicitar la propuesta",
        }),
      }),
  });
  const downloadFile = useDownloadSireFileMutation({
    onError: (err, variables) =>
      addNotification({
        variant: "danger",
        ...getMutationErrorMessage({
          error: err,
          fallback: `No se pudo descargar ${FILE_LABELS[variables.kind]}`,
        }),
      }),
  });

  const isBeta = company.sunat_env === "beta";
  const startOver = isStartOver(period);
  const blocker = requestBlocker(period);
  const requestGate = isBeta
    ? {
        isAriaDisabled: true,
        tooltipProps: {
          content: "SUNAT no tiene ambiente de pruebas para SIRE.",
        },
      }
    : !can("operator")
      ? disabledProps("operator")
      : startOver && !can("admin")
        ? {
            isAriaDisabled: true,
            tooltipProps: {
              content:
                "Volver a solicitar una propuesta revisada o aceptada requiere el rol Administrador.",
            },
          }
        : blocker
          ? { isAriaDisabled: true, tooltipProps: { content: blocker } }
          : {};
  const replaceGate = !can("admin")
    ? disabledProps("admin")
    : !period || !isSireReplaceable(period)
      ? {
          isAriaDisabled: true,
          tooltipProps: {
            content:
              "Solicita primero la propuesta de SUNAT y revisa la conciliación.",
          },
        }
      : {};
  const fileGate = (available: boolean | undefined) =>
    available
      ? {}
      : {
          isAriaDisabled: true,
          tooltipProps: { content: "SUNAT aún no tiene este archivo." },
        };

  const onRequest = () => {
    if (startOver) {
      setIsStartOverOpen(true);
    } else {
      requestProposal.mutate(path);
    }
  };
  const onDownload = (kind: SireFileKind) =>
    downloadFile.mutate({ ...path, kind });

  const title = company.razon_social || company.ruc || companyId;
  const reconciliation = period ? sireReconciliation(period) : undefined;

  const body = () => {
    if (isLoading) {
      return (
        <Bullseye>
          <Spinner aria-label="Cargando periodo" />
        </Bullseye>
      );
    }
    if (isNotRequested) {
      return (
        <EmptyState
          variant="sm"
          icon={CubesIcon}
          titleText="Aún no se solicitó la propuesta de este periodo"
          headingLevel="h2"
        >
          <EmptyStateBody>
            Al solicitarla, SUNAT prepara su propuesta del registro y la
            plataforma la concilia con los comprobantes registrados.
          </EmptyStateBody>
        </EmptyState>
      );
    }
    if (error) {
      return (
        <Alert variant="danger" isInline title="No se pudo cargar el periodo">
          <ApiErrorText
            error={getMutationErrorMessage({
              error,
              fallback: "No se pudo cargar el periodo",
            })}
          />
        </Alert>
      );
    }
    if (!period) return null;
    return (
      <Stack hasGutter>
        <StackItem>
          <Card>
            <CardTitle>Estado del periodo</CardTitle>
            <CardBody>
              <DescriptionList
                aria-label="Estado del periodo"
                columnModifier={{ md: "2Col" }}
              >
                <DescriptionListGroup>
                  <DescriptionListTerm>Estado</DescriptionListTerm>
                  <DescriptionListDescription>
                    <SireStatusLabel status={period.status} />
                  </DescriptionListDescription>
                </DescriptionListGroup>
                <DescriptionListGroup>
                  <DescriptionListTerm>Ambiente SUNAT</DescriptionListTerm>
                  <DescriptionListDescription>
                    <SunatEnvLabel sunatEnv={period.sunat_env} />
                  </DescriptionListDescription>
                </DescriptionListGroup>
                <DescriptionListGroup>
                  <DescriptionListTerm>Detalle</DescriptionListTerm>
                  <DescriptionListDescription>
                    {period.detail || "-"}
                  </DescriptionListDescription>
                </DescriptionListGroup>
                <DescriptionListGroup>
                  <DescriptionListTerm>Actualizado</DescriptionListTerm>
                  <DescriptionListDescription>
                    {period.updated_at
                      ? dayjs(period.updated_at).format(RENDER_DATETIME_FORMAT)
                      : "-"}
                  </DescriptionListDescription>
                </DescriptionListGroup>
              </DescriptionList>
            </CardBody>
          </Card>
        </StackItem>
        <StackItem>
          <Card>
            <CardTitle>Conciliación</CardTitle>
            <CardBody>
              {reconciliation ? (
                <SireReconciliation reconciliation={reconciliation} />
              ) : (
                <Content component={ContentVariants.p}>
                  La conciliación aparece cuando llega la propuesta de SUNAT.
                </Content>
              )}
            </CardBody>
          </Card>
        </StackItem>
      </Stack>
    );
  };

  return (
    <>
      <DocumentTitle title={`SIRE - ${title}`} />
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
          <BreadcrumbItem isActive>SIRE</BreadcrumbItem>
        </Breadcrumb>
        <Content component={ContentVariants.h1}>SIRE</Content>
        <Content component={ContentVariants.p}>
          Los registros de ventas y compras de {title} ante SUNAT, por periodo.
        </Content>
      </PageSection>
      <PageSection>
        <Stack hasGutter>
          {isBeta ? (
            <StackItem>
              <Alert
                variant="info"
                isInline
                title="SUNAT no tiene ambiente de pruebas para SIRE"
              >
                La empresa está en el ambiente beta: SIRE funciona en producción
                o con el simulador de SUNAT.
              </Alert>
            </StackItem>
          ) : null}
          <StackItem>
            <Toolbar aria-label="Periodo de SIRE">
              <ToolbarContent>
                <ToolbarGroup>
                  <ToolbarItem>
                    <ToggleGroup aria-label="Registro">
                      {SIRE_BOOKS.map((book) => (
                        <ToggleGroupItem
                          key={book}
                          text={BOOK_LABELS[book]}
                          buttonId={`sire-book-${book}`}
                          isSelected={selection.libro === book}
                          onChange={() =>
                            setSelection((prev) => ({ ...prev, libro: book }))
                          }
                        />
                      ))}
                    </ToggleGroup>
                  </ToolbarItem>
                  <ToolbarItem>
                    <FormSelect
                      aria-label="Periodo"
                      value={selection.periodo}
                      onChange={(_event, value) =>
                        setSelection((prev) => ({ ...prev, periodo: value }))
                      }
                    >
                      {/* A period from a link may be older than the ones offered. */}
                      {PERIOD_OPTIONS.some(
                        (option) => option.value === selection.periodo,
                      ) ? null : (
                        <FormSelectOption
                          value={selection.periodo}
                          label={selection.periodo}
                        />
                      )}
                      {PERIOD_OPTIONS.map((option) => (
                        <FormSelectOption
                          key={option.value}
                          value={option.value}
                          label={option.label}
                        />
                      ))}
                    </FormSelect>
                  </ToolbarItem>
                </ToolbarGroup>
                <ToolbarGroup>
                  <ToolbarItem>
                    <GatedButton
                      variant="primary"
                      onClick={onRequest}
                      isLoading={requestProposal.isPending}
                      isDisabled={requestProposal.isPending}
                      {...requestGate}
                    >
                      Solicitar propuesta
                    </GatedButton>
                  </ToolbarItem>
                  <ToolbarItem>
                    <GatedButton
                      variant="secondary"
                      onClick={() => period && setReplaceTarget(period)}
                      {...replaceGate}
                    >
                      Reemplazar propuesta
                    </GatedButton>
                  </ToolbarItem>
                  <ToolbarItem>
                    <GatedButton
                      variant="link"
                      onClick={() => onDownload("proposal")}
                      {...fileGate(period?.proposal_file)}
                    >
                      Descargar propuesta
                    </GatedButton>
                  </ToolbarItem>
                  <ToolbarItem>
                    <GatedButton
                      variant="link"
                      onClick={() => onDownload("replacement")}
                      {...fileGate(period?.replacement_file)}
                    >
                      Descargar reemplazo
                    </GatedButton>
                  </ToolbarItem>
                </ToolbarGroup>
              </ToolbarContent>
            </Toolbar>
          </StackItem>
          <StackItem>{body()}</StackItem>
        </Stack>
      </PageSection>

      <ConfirmActionModal
        isOpen={isStartOverOpen}
        title="¿Volver a solicitar la propuesta?"
        body={
          period?.status === "accepted"
            ? "SUNAT ya aceptó este registro. Una nueva propuesta descarta esa aceptación y la conciliación actual."
            : "Una nueva propuesta descarta la conciliación que se está revisando."
        }
        confirmLabel="Solicitar de nuevo"
        cancelLabel="Volver"
        confirmVariant="primary"
        titleIconVariant="warning"
        isConfirming={requestProposal.isPending}
        onConfirm={() =>
          requestProposal.mutate(path, {
            onSuccess: () => setIsStartOverOpen(false),
          })
        }
        onCancel={() => setIsStartOverOpen(false)}
      />
      <SireReplaceModal
        path={path}
        period={replaceTarget}
        onClose={() => setReplaceTarget(null)}
      />
    </>
  );
};
