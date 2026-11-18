import type React from "react";

import {
  Alert,
  Bullseye,
  Button,
  Card,
  CardBody,
  CardFooter,
  CardTitle,
  Content,
  ContentVariants,
  DescriptionList,
  DescriptionListDescription,
  DescriptionListGroup,
  DescriptionListTerm,
  EmptyState,
  EmptyStateBody,
  Flex,
  FlexItem,
  PageSection,
  Spinner,
} from "@patternfly/react-core";
import DownloadIcon from "@patternfly/react-icons/dist/esm/icons/download-icon";
import ExclamationCircleIcon from "@patternfly/react-icons/dist/esm/icons/exclamation-circle-icon";
import PathMissingIcon from "@patternfly/react-icons/dist/esm/icons/path-missing-icon";

import { DocumentTitle } from "@app/components/DocumentTitle";
import { useNotifications } from "@app/hooks/useNotifications";
import {
  isPublicDocumentFileKind,
  usePublicDocumentFileDownloadMutation,
  usePublicDocumentQuery,
} from "@app/queries/xhandler/public-documents";
import { getMutationErrorMessage } from "@app/utils/apiError";
import { isNotFoundError } from "@app/utils/isHttpError";

import {
  formatTotal,
  PUBLIC_DOCUMENT_FILE_LABELS,
  publicDocumentStatusLabel,
  publicFileOrder,
  tipoComprobanteLabel,
} from "./documentPresentation";

interface IDocumentAccessProps {
  token: string;
}

/**
 * The buyer portal (DESIGN.md §3.3, §12, ADR 0003): what a capability link shows without a
 * session — the document, its status, its files and the issuer's Libro de Reclamaciones.
 */
export const DocumentAccess: React.FC<IDocumentAccessProps> = ({ token }) => {
  const { addNotification } = useNotifications();
  const { data, isLoading, error } = usePublicDocumentQuery({ token });
  const download = usePublicDocumentFileDownloadMutation(token, {
    onError: (err, { kind }) =>
      addNotification({
        variant: "danger",
        ...getMutationErrorMessage({
          error: err,
          fallback: `No se pudo descargar el ${PUBLIC_DOCUMENT_FILE_LABELS[kind]}`,
        }),
      }),
  });

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
    const files = publicFileOrder(
      (data.files ?? []).filter(isPublicDocumentFileKind),
    );
    return (
      <Card>
        <CardTitle>
          {tipoComprobanteLabel(data.tipo_comprobante)} {data.serie_numero}
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
                {formatTotal(data.total, data.moneda)}
              </DescriptionListDescription>
            </DescriptionListGroup>
            <DescriptionListGroup>
              <DescriptionListTerm>Estado</DescriptionListTerm>
              <DescriptionListDescription>
                {publicDocumentStatusLabel(data.status)}
              </DescriptionListDescription>
            </DescriptionListGroup>
          </DescriptionList>
          {data.status === "documento_de_prueba" ? (
            <Alert
              isInline
              isPlain
              variant="warning"
              title="Este comprobante se emitió en un ambiente de pruebas y no tiene validez tributaria."
            />
          ) : null}
        </CardBody>
        <CardBody>
          <Flex
            spaceItems={{ default: "spaceItemsSm" }}
            aria-label="Descargas del comprobante"
          >
            {files.map((kind) => (
              <FlexItem key={kind}>
                <Button
                  variant={kind === "pdf" ? "primary" : "secondary"}
                  icon={<DownloadIcon />}
                  isLoading={
                    download.isPending && download.variables?.kind === kind
                  }
                  isDisabled={download.isPending}
                  onClick={() =>
                    download.mutate({ kind, serieNumero: data.serie_numero })
                  }
                >
                  Descargar {PUBLIC_DOCUMENT_FILE_LABELS[kind]}
                </Button>
              </FlexItem>
            ))}
          </Flex>
        </CardBody>
        {data.libro_reclamaciones_url ? (
          <CardFooter>
            <Button
              component="a"
              variant="link"
              isInline
              href={data.libro_reclamaciones_url}
              target="_blank"
              rel="noopener noreferrer"
            >
              Libro de Reclamaciones de {data.emisor_razon_social}
            </Button>
          </CardFooter>
        ) : null}
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
