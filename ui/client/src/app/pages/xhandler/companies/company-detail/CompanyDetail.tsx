import type React from "react";
import { useState } from "react";

import { Link } from "@tanstack/react-router";
import dayjs from "dayjs";

import {
  Breadcrumb,
  BreadcrumbItem,
  Card,
  CardBody,
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
  Tab,
  Tabs,
  TabTitleText,
} from "@patternfly/react-core";

import { DocumentTitle } from "@app/components/DocumentTitle";
import { GatedButton } from "@app/components/GatedButton";
import { SunatEnvLabel } from "@app/components/SunatEnvLabel";
import { RENDER_DATETIME_FORMAT } from "@app/constants";
import { useNotifications } from "@app/hooks/useNotifications";
import { usePersistentState } from "@app/hooks/usePersistentState";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";
import { useTenantRole } from "@app/hooks/useTenantRole";
import {
  useCompanyVerifyMutation,
  useSuspenseCompanyDetailQuery,
} from "@app/queries/xhandler/companies";
import { getMutationErrorMessage } from "@app/utils/apiError";

import {
  COMPANY_DETAIL_STATE,
  type CompanyDetailTab,
  isCompanyDetailTab,
} from "./companyDetailState";
import { CertificatePanel } from "./components/CertificatePanel";
import { CompanySettingsForm } from "./components/CompanySettingsForm";
import { CredentialsPanel } from "./components/CredentialsPanel";
import { EnvironmentModal } from "./components/EnvironmentModal";
import { LogoUploadModal } from "./components/LogoUploadModal";
import { RoutePanel } from "./components/RoutePanel";
import { SeriesPanel } from "./components/SeriesPanel";

interface ICompanyDetailProps {
  companyId: string;
}

const EMISSION_MODE_LABELS: Record<string, string> = {
  software: "Sistema del contribuyente (certificado propio)",
  pse: "PSE (firma la plataforma)",
};

const SUBMISSION_ROUTE_LABELS: Record<string, string> = {
  sunat: "SUNAT",
  ose: "OSE",
};

const TAB_TITLES: Record<CompanyDetailTab, string> = {
  resumen: "Resumen",
  configuracion: "Configuración",
  certificado: "Certificado",
  series: "Series",
  credenciales: "Credenciales",
  envio: "Envío a SUNAT",
};

/** One company (RUC) of the tenant in the URL: the bottom of partner → tenant → company. */
export const CompanyDetail: React.FC<ICompanyDetailProps> = ({ companyId }) => {
  const tenantId = useSelectedTenantId();
  const { addNotification } = useNotifications();
  const { disabledProps } = useTenantRole();
  const { data: company } = useSuspenseCompanyDetailQuery({ companyId });
  const title = company.razon_social || company.ruc || companyId;
  const [tab, setTab] = usePersistentState({
    ...COMPANY_DETAIL_STATE.tab,
    storage: "url",
  });
  const [isEnvironmentOpen, setIsEnvironmentOpen] = useState(false);
  const [isLogoOpen, setIsLogoOpen] = useState(false);

  const verify = useCompanyVerifyMutation(companyId, {
    onSuccess: (data) =>
      addNotification({
        variant: "success",
        title: `Se verificó el RUC ${data.ruc ?? ""}`.trim(),
      }),
    onError: (error) =>
      addNotification({
        variant: "danger",
        ...getMutationErrorMessage({
          error,
          fallback: "No se pudo verificar el RUC",
        }),
      }),
  });

  const fields: { label: string; value: React.ReactNode }[] = [
    { label: "RUC", value: company.ruc ?? "-" },
    { label: "Razón social", value: company.razon_social ?? "-" },
    { label: "Nombre comercial", value: company.nombre_comercial || "-" },
    { label: "Dirección", value: company.direccion || "-" },
    { label: "Correo", value: company.email || "-" },
    {
      label: "Ambiente SUNAT",
      value: <SunatEnvLabel sunatEnv={company.sunat_env} />,
    },
    {
      label: "Modo de emisión",
      value:
        EMISSION_MODE_LABELS[company.emission_mode ?? ""] ??
        company.emission_mode ??
        "-",
    },
    {
      label: "Ruta de envío",
      value:
        SUBMISSION_ROUTE_LABELS[company.submission_route ?? ""] ??
        company.submission_route ??
        "-",
    },
    {
      label: "RUC verificado",
      value: company.ruc_verified_at
        ? dayjs(company.ruc_verified_at).format(RENDER_DATETIME_FORMAT)
        : "Sin verificar",
    },
    {
      label: "Envío a SUNAT",
      value: company.sending_paused ? "En pausa" : "Activo",
    },
    { label: "Logo", value: company.has_logo ? "Cargado" : "Sin logo" },
  ];

  return (
    <>
      <DocumentTitle title={title} />
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
          <BreadcrumbItem isActive>{company.ruc}</BreadcrumbItem>
        </Breadcrumb>
        <Flex
          justifyContent={{ default: "justifyContentSpaceBetween" }}
          alignItems={{ default: "alignItemsCenter" }}
        >
          <FlexItem>
            <Content component={ContentVariants.h1}>{title}</Content>
            <Flex spaceItems={{ default: "spaceItemsSm" }}>
              <SunatEnvLabel sunatEnv={company.sunat_env} />
              {company.ruc_verified_at ? (
                <Label color="green" isCompact>
                  RUC verificado
                </Label>
              ) : (
                <Label color="orange" isCompact>
                  RUC sin verificar
                </Label>
              )}
            </Flex>
          </FlexItem>
          <FlexItem>
            <Flex spaceItems={{ default: "spaceItemsSm" }}>
              <GatedButton
                variant="secondary"
                onClick={() => verify.mutate()}
                isLoading={verify.isPending}
                isDisabled={verify.isPending}
                {...disabledProps("admin")}
              >
                Verificar RUC
              </GatedButton>
              <GatedButton
                variant="secondary"
                onClick={() => setIsEnvironmentOpen(true)}
                {...disabledProps("admin")}
              >
                Cambiar ambiente
              </GatedButton>
              <GatedButton
                variant="secondary"
                onClick={() => setIsLogoOpen(true)}
                {...disabledProps("admin")}
              >
                Subir logo
              </GatedButton>
            </Flex>
          </FlexItem>
        </Flex>
      </PageSection>
      <PageSection>
        <Tabs
          activeKey={tab.pestana}
          onSelect={(_event, key) => {
            const next = String(key);
            if (isCompanyDetailTab(next)) setTab({ pestana: next });
          }}
          aria-label="Secciones de la empresa"
          mountOnEnter
          unmountOnExit
        >
          <Tab
            eventKey="resumen"
            title={<TabTitleText>{TAB_TITLES.resumen}</TabTitleText>}
          >
            <Card>
              <CardBody>
                <DescriptionList
                  aria-label="Datos de la empresa"
                  columnModifier={{ md: "2Col" }}
                >
                  {fields.map(({ label, value }) => (
                    <DescriptionListGroup key={label}>
                      <DescriptionListTerm>{label}</DescriptionListTerm>
                      <DescriptionListDescription>
                        {value}
                      </DescriptionListDescription>
                    </DescriptionListGroup>
                  ))}
                </DescriptionList>
              </CardBody>
            </Card>
          </Tab>
          <Tab
            eventKey="configuracion"
            title={<TabTitleText>{TAB_TITLES.configuracion}</TabTitleText>}
          >
            <CompanySettingsForm company={company} />
          </Tab>
          <Tab
            eventKey="certificado"
            title={<TabTitleText>{TAB_TITLES.certificado}</TabTitleText>}
          >
            <CertificatePanel companyId={companyId} companyRuc={company.ruc} />
          </Tab>
          <Tab
            eventKey="series"
            title={<TabTitleText>{TAB_TITLES.series}</TabTitleText>}
          >
            <SeriesPanel companyId={companyId} sunatEnv={company.sunat_env} />
          </Tab>
          <Tab
            eventKey="credenciales"
            title={<TabTitleText>{TAB_TITLES.credenciales}</TabTitleText>}
          >
            <CredentialsPanel companyId={companyId} />
          </Tab>
          <Tab
            eventKey="envio"
            title={<TabTitleText>{TAB_TITLES.envio}</TabTitleText>}
          >
            <RoutePanel companyId={companyId} />
          </Tab>
        </Tabs>
      </PageSection>

      <EnvironmentModal
        isOpen={isEnvironmentOpen}
        company={company}
        onClose={() => setIsEnvironmentOpen(false)}
      />
      <LogoUploadModal
        isOpen={isLogoOpen}
        companyId={companyId}
        onClose={() => setIsLogoOpen(false)}
      />
    </>
  );
};
