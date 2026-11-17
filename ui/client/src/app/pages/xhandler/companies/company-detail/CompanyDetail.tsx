import type React from "react";

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
  PageSection,
} from "@patternfly/react-core";

import { DocumentTitle } from "@app/components/DocumentTitle";
import { SunatEnvLabel } from "@app/components/SunatEnvLabel";
import { RENDER_DATETIME_FORMAT } from "@app/constants";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";
import { useSuspenseCompanyDetailQuery } from "@app/queries/xhandler/companies";

interface ICompanyDetailProps {
  companyId: string;
}

const EMISSION_MODE_LABELS: Record<string, string> = {
  software: "Sistema del contribuyente (certificado propio)",
  pse: "PSE (firma la plataforma)",
};

/** One company (RUC) of the tenant in the URL: the bottom of partner → tenant → company. */
export const CompanyDetail: React.FC<ICompanyDetailProps> = ({ companyId }) => {
  const tenantId = useSelectedTenantId();
  const { data: company } = useSuspenseCompanyDetailQuery({ companyId });
  const title = company.razon_social || company.ruc || companyId;

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
      label: "RUC verificado",
      value: company.ruc_verified_at
        ? dayjs(company.ruc_verified_at).format(RENDER_DATETIME_FORMAT)
        : "Sin verificar",
    },
    {
      label: "Envío a SUNAT",
      value: company.sending_paused ? "En pausa" : "Activo",
    },
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
        <Content component={ContentVariants.h1}>{title}</Content>
      </PageSection>
      <PageSection>
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
      </PageSection>
    </>
  );
};
