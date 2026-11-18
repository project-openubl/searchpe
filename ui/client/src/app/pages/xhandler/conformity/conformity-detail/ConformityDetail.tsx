import type React from "react";

import { Link } from "@tanstack/react-router";
import dayjs from "dayjs";

import {
  Alert,
  Breadcrumb,
  BreadcrumbItem,
  Card,
  CardBody,
  CardTitle,
  ClipboardCopy,
  ClipboardCopyVariant,
  Content,
  ContentVariants,
  DescriptionList,
  DescriptionListDescription,
  DescriptionListGroup,
  DescriptionListTerm,
  PageSection,
  Stack,
  StackItem,
} from "@patternfly/react-core";

import { ConformityStateLabel } from "@app/components/ConformityStateLabel";
import { DocumentTitle } from "@app/components/DocumentTitle";
import { TenantContextSelector } from "@app/components/TenantContextSelector";
import { RENDER_DATETIME_FORMAT } from "@app/constants";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";
import { useSuspenseCompanyDetailQuery } from "@app/queries/xhandler/companies";
import { useSuspenseDocumentConformityQuery } from "@app/queries/xhandler/conformity";

interface IConformityDetailProps {
  companyId: string;
  documentId: string;
}

const formatTime = (value: string | undefined): string =>
  value ? dayjs(value).format(RENDER_DATETIME_FORMAT) : "-";

/**
 * One factura negociable's conformity (DESIGN.md §11.8). After an objection the API suggests the
 * credit note type 13 that corrects the cuotas, to complete and emit.
 */
export const ConformityDetail: React.FC<IConformityDetailProps> = ({
  companyId,
  documentId,
}) => {
  const tenantId = useSelectedTenantId();
  const { data: company } = useSuspenseCompanyDetailQuery({ companyId });
  const { data: conformity } = useSuspenseDocumentConformityQuery({
    documentId,
  });
  const title = conformity.serie_numero ?? documentId;

  const fields: { label: string; value: React.ReactNode }[] = [
    { label: "Factura", value: conformity.serie_numero ?? "-" },
    { label: "RUC del adquirente", value: conformity.buyer_ruc ?? "-" },
    {
      label: "Conformidad",
      value: <ConformityStateLabel state={conformity.state} />,
    },
    {
      label: "Aceptada por SUNAT",
      value: formatTime(conformity.accepted_at),
    },
    { label: "Vence el plazo", value: formatTime(conformity.window_ends_at) },
    { label: "Resuelta", value: formatTime(conformity.resolved_at) },
    {
      label: "Última consulta a SUNAT",
      value: formatTime(conformity.checked_at),
    },
    {
      label: "Resultado de la consulta",
      value: conformity.check_detail || "-",
    },
  ];

  return (
    <>
      <DocumentTitle title={`Conformidad ${title}`} />
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
          <BreadcrumbItem>
            <Link
              to="/organizaciones/$tenantId/empresas/$companyId/conformidad"
              params={{ tenantId, companyId }}
            >
              Facturas negociables
            </Link>
          </BreadcrumbItem>
          <BreadcrumbItem isActive>{title}</BreadcrumbItem>
        </Breadcrumb>
        <Content component={ContentVariants.h1}>Conformidad {title}</Content>
      </PageSection>
      <PageSection>
        <Stack hasGutter>
          <StackItem>
            <Card>
              <CardBody>
                <DescriptionList
                  aria-label="Conformidad de la factura"
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
          </StackItem>
          {conformity.suggested_credit_note ? (
            <StackItem>
              <Card>
                <CardTitle component="h2">
                  Nota de crédito tipo 13 sugerida
                </CardTitle>
                <CardBody>
                  <Stack hasGutter>
                    <StackItem>
                      <Alert
                        variant="info"
                        isInline
                        title="El adquirente objetó la factura"
                      >
                        Corrige las cuotas o el monto neto pendiente con una
                        nota de crédito tipo 13 (ajuste de montos y/o fechas de
                        pago). Completa las cuotas corregidas y los detalles de
                        este borrador y emítelo como un documento nuevo.
                      </Alert>
                    </StackItem>
                    <StackItem>
                      <ClipboardCopy
                        isCode
                        isReadOnly
                        isExpanded
                        variant={ClipboardCopyVariant.expansion}
                        hoverTip="Copiar"
                        clickTip="Copiado"
                        textAriaLabel="Nota de crédito sugerida"
                        toggleAriaLabel="Mostrar la nota de crédito sugerida"
                      >
                        {JSON.stringify(
                          conformity.suggested_credit_note,
                          null,
                          2,
                        )}
                      </ClipboardCopy>
                    </StackItem>
                  </Stack>
                </CardBody>
              </Card>
            </StackItem>
          ) : null}
        </Stack>
      </PageSection>
    </>
  );
};
