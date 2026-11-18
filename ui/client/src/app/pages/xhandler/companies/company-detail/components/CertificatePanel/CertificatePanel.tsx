import type React from "react";
import { useState } from "react";

import dayjs from "dayjs";

import {
  Alert,
  Card,
  CardBody,
  CardHeader,
  CardTitle,
  DescriptionList,
  DescriptionListDescription,
  DescriptionListGroup,
  DescriptionListTerm,
  EmptyState,
  EmptyStateBody,
  Label,
  Spinner,
} from "@patternfly/react-core";
import KeyIcon from "@patternfly/react-icons/dist/esm/icons/key-icon";

import { GatedButton } from "@app/components/GatedButton";
import { RENDER_DATETIME_FORMAT } from "@app/constants";
import { useTenantRole } from "@app/hooks/useTenantRole";
import {
  certificateDaysLeft,
  certificateStanding,
  useCertificateQuery,
} from "@app/queries/xhandler/certificates";
import { getMutationErrorMessage } from "@app/utils/apiError";
import { isNotFoundError } from "@app/utils/isHttpError";

import { CertificateStandingLabel } from "./CertificateStandingLabel";
import { CertificateUploadModal } from "./CertificateUploadModal";

interface ICertificatePanelProps {
  companyId: string;
  companyRuc: string | undefined;
}

const formatTime = (value: string | undefined): string =>
  value ? dayjs(value).format(RENDER_DATETIME_FORMAT) : "-";

/** The company's signing certificate and its expiry; the private key never leaves the vault. */
export const CertificatePanel: React.FC<ICertificatePanelProps> = ({
  companyId,
  companyRuc,
}) => {
  const { disabledProps } = useTenantRole();
  const [isUploadOpen, setIsUploadOpen] = useState(false);
  const {
    data: certificate,
    isLoading,
    error,
  } = useCertificateQuery({
    companyId,
  });
  const hasNone = isNotFoundError(error);
  const daysLeft = certificateDaysLeft({
    validUntil: certificate?.valid_until,
  });
  const standing = certificateStanding(daysLeft);

  const body = (() => {
    if (isLoading) return <Spinner aria-label="Cargando certificado" />;
    if (hasNone) {
      return (
        <EmptyState
          variant="sm"
          icon={KeyIcon}
          titleText="La empresa no tiene certificado digital"
          headingLevel="h3"
        >
          <EmptyStateBody>
            Sube el certificado (.pfx) con el que se firman los comprobantes en
            modo sistema del contribuyente.
          </EmptyStateBody>
        </EmptyState>
      );
    }
    if (error || !certificate) {
      const message = getMutationErrorMessage({
        error,
        fallback: "No se pudo cargar el certificado",
      });
      return (
        <Alert variant="danger" isInline title={message.title}>
          {message.description}
        </Alert>
      );
    }
    return (
      <DescriptionList
        aria-label="Datos del certificado"
        columnModifier={{ md: "2Col" }}
      >
        <DescriptionListGroup>
          <DescriptionListTerm>Estado</DescriptionListTerm>
          <DescriptionListDescription>
            <CertificateStandingLabel standing={standing} daysLeft={daysLeft} />
          </DescriptionListDescription>
        </DescriptionListGroup>
        <DescriptionListGroup>
          <DescriptionListTerm>RUC del certificado</DescriptionListTerm>
          <DescriptionListDescription>
            {certificate.ruc ?? "-"}{" "}
            {certificate.ruc_matches ? (
              <Label color="green" isCompact>
                Coincide con la empresa
              </Label>
            ) : (
              <Label color="orange" isCompact>
                {`No coincide con ${companyRuc ?? "la empresa"}`}
              </Label>
            )}
          </DescriptionListDescription>
        </DescriptionListGroup>
        <DescriptionListGroup>
          <DescriptionListTerm>Titular</DescriptionListTerm>
          <DescriptionListDescription>
            {certificate.subject ?? "-"}
          </DescriptionListDescription>
        </DescriptionListGroup>
        <DescriptionListGroup>
          <DescriptionListTerm>Emisor</DescriptionListTerm>
          <DescriptionListDescription>
            {certificate.issuer ?? "-"}
          </DescriptionListDescription>
        </DescriptionListGroup>
        <DescriptionListGroup>
          <DescriptionListTerm>Número de serie</DescriptionListTerm>
          <DescriptionListDescription>
            {certificate.serial ?? "-"}
          </DescriptionListDescription>
        </DescriptionListGroup>
        <DescriptionListGroup>
          <DescriptionListTerm>Vigencia</DescriptionListTerm>
          <DescriptionListDescription>
            {`${formatTime(certificate.valid_from)} – ${formatTime(certificate.valid_until)}`}
          </DescriptionListDescription>
        </DescriptionListGroup>
      </DescriptionList>
    );
  })();

  return (
    <>
      <Card>
        <CardHeader
          actions={{
            actions: (
              <GatedButton
                variant="secondary"
                onClick={() => setIsUploadOpen(true)}
                {...disabledProps("admin")}
              >
                {certificate ? "Reemplazar certificado" : "Subir certificado"}
              </GatedButton>
            ),
            hasNoOffset: true,
          }}
        >
          <CardTitle component="h2">Certificado digital</CardTitle>
        </CardHeader>
        <CardBody>{body}</CardBody>
      </Card>
      <CertificateUploadModal
        isOpen={isUploadOpen}
        companyId={companyId}
        onClose={() => setIsUploadOpen(false)}
      />
    </>
  );
};
