import type React from "react";

import {
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
  PageSection,
  Spinner,
} from "@patternfly/react-core";
import ExclamationCircleIcon from "@patternfly/react-icons/dist/esm/icons/exclamation-circle-icon";
import PathMissingIcon from "@patternfly/react-icons/dist/esm/icons/path-missing-icon";

import { DocumentTitle } from "@app/components/DocumentTitle";
import { usePublicDocumentQuery } from "@app/queries/xhandler/public-documents";
import { isNotFoundError } from "@app/utils/isHttpError";

interface IDocumentAccessProps {
  token: string;
}

/**
 * The buyer portal (DESIGN.md §12, ADR 0003): what a capability link shows without a session.
 * U0 placeholder: the document's summary; downloads land with U2.
 */
export const DocumentAccess: React.FC<IDocumentAccessProps> = ({ token }) => {
  const { data, isLoading, error } = usePublicDocumentQuery({ token });

  const renderBody = () => {
    if (isLoading) {
      return (
        <Bullseye>
          <Spinner aria-label="Cargando comprobante" />
        </Bullseye>
      );
    }
    if (isNotFoundError(error)) {
      return (
        <EmptyState
          titleText="Enlace no válido"
          headingLevel="h2"
          icon={PathMissingIcon}
        >
          <EmptyStateBody>
            Este enlace no existe, fue revocado o venció. Pide un nuevo enlace a
            quien emitió el comprobante.
          </EmptyStateBody>
        </EmptyState>
      );
    }
    if (error || !data) {
      return (
        <EmptyState
          titleText="No se pudo cargar el comprobante"
          headingLevel="h2"
          icon={ExclamationCircleIcon}
          status="danger"
        >
          <EmptyStateBody>Inténtalo de nuevo en unos minutos.</EmptyStateBody>
        </EmptyState>
      );
    }
    return (
      <Card>
        <CardTitle>
          {data.tipo_comprobante} {data.serie_numero}
        </CardTitle>
        <CardBody>
          <DescriptionList aria-label="Datos del comprobante">
            <DescriptionListGroup>
              <DescriptionListTerm>Emisor</DescriptionListTerm>
              <DescriptionListDescription>
                {data.emisor_razon_social} (RUC {data.emisor_ruc})
              </DescriptionListDescription>
            </DescriptionListGroup>
            <DescriptionListGroup>
              <DescriptionListTerm>Fecha de emisión</DescriptionListTerm>
              <DescriptionListDescription>
                {data.fecha_emision ?? "-"}
              </DescriptionListDescription>
            </DescriptionListGroup>
            <DescriptionListGroup>
              <DescriptionListTerm>Total</DescriptionListTerm>
              <DescriptionListDescription>
                {data.moneda} {data.total?.toLocaleString("es-PE") ?? "-"}
              </DescriptionListDescription>
            </DescriptionListGroup>
            <DescriptionListGroup>
              <DescriptionListTerm>Estado</DescriptionListTerm>
              <DescriptionListDescription>
                {data.status ?? "-"}
              </DescriptionListDescription>
            </DescriptionListGroup>
          </DescriptionList>
        </CardBody>
      </Card>
    );
  };

  return (
    <>
      <DocumentTitle title="Comprobante electrónico" />
      <PageSection>
        <Content component={ContentVariants.h1}>
          Comprobante electrónico
        </Content>
      </PageSection>
      <PageSection>{renderBody()}</PageSection>
    </>
  );
};
