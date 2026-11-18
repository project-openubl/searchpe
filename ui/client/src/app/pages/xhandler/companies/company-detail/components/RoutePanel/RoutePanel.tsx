import type React from "react";
import { useState } from "react";

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
  Label,
  Spinner,
} from "@patternfly/react-core";

import { GatedButton } from "@app/components/GatedButton";
import { useTenantRole } from "@app/hooks/useTenantRole";
import { useCompanyRouteQuery } from "@app/queries/xhandler/companies";
import { getMutationErrorMessage } from "@app/utils/apiError";

import { isSubmissionRoute, SUBMISSION_ROUTE_LABELS } from "./routeForm";
import { RouteModal } from "./RouteModal";

interface IRoutePanelProps {
  companyId: string;
}

/** Where the company's documents go (SUNAT or an OSE) and whether SUNAT obliges it to use an OSE. */
export const RoutePanel: React.FC<IRoutePanelProps> = ({ companyId }) => {
  const { disabledProps } = useTenantRole();
  const [isEditing, setIsEditing] = useState(false);
  const {
    data: route,
    isLoading,
    error,
  } = useCompanyRouteQuery({
    companyId,
  });
  const current = route?.submission_route ?? "";

  const body = (() => {
    if (isLoading) return <Spinner aria-label="Cargando ruta de envío" />;
    if (error || !route) {
      const message = getMutationErrorMessage({
        error,
        fallback: "No se pudo cargar la ruta de envío",
      });
      return (
        <Alert variant="danger" isInline title={message.title}>
          {message.description}
        </Alert>
      );
    }
    return (
      <DescriptionList
        aria-label="Ruta de envío"
        columnModifier={{ md: "2Col" }}
      >
        <DescriptionListGroup>
          <DescriptionListTerm>Ruta</DescriptionListTerm>
          <DescriptionListDescription>
            {isSubmissionRoute(current)
              ? SUBMISSION_ROUTE_LABELS[current]
              : current || "-"}
          </DescriptionListDescription>
        </DescriptionListGroup>
        <DescriptionListGroup>
          <DescriptionListTerm>
            Obligada a emitir mediante OSE
          </DescriptionListTerm>
          <DescriptionListDescription>
            {route.ose_obligated ? (
              <Label color="orange" isCompact>
                Sí, designada por SUNAT
              </Label>
            ) : (
              "No"
            )}
          </DescriptionListDescription>
        </DescriptionListGroup>
        {route.ose_name ? (
          <>
            <DescriptionListGroup>
              <DescriptionListTerm>OSE</DescriptionListTerm>
              <DescriptionListDescription>
                {route.ose_name}
              </DescriptionListDescription>
            </DescriptionListGroup>
            <DescriptionListGroup>
              <DescriptionListTerm>URL de comprobantes</DescriptionListTerm>
              <DescriptionListDescription>
                {route.ose_invoice_url ?? "-"}
              </DescriptionListDescription>
            </DescriptionListGroup>
            <DescriptionListGroup>
              <DescriptionListTerm>
                URL de percepciones y retenciones
              </DescriptionListTerm>
              <DescriptionListDescription>
                {route.ose_perception_url ?? "-"}
              </DescriptionListDescription>
            </DescriptionListGroup>
          </>
        ) : null}
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
                onClick={() => setIsEditing(true)}
                isDisabled={!route}
                {...disabledProps("admin")}
              >
                Cambiar ruta
              </GatedButton>
            ),
            hasNoOffset: true,
          }}
        >
          <CardTitle component="h2">Envío de comprobantes</CardTitle>
        </CardHeader>
        <CardBody>
          {route?.ose_obligated && current === "sunat" ? (
            <Alert
              variant="warning"
              isInline
              isPlain
              title="La empresa está obligada a emitir a través de un OSE: configura uno."
            />
          ) : null}
          {body}
        </CardBody>
      </Card>
      <RouteModal
        isOpen={isEditing}
        companyId={companyId}
        route={route}
        onClose={() => setIsEditing(false)}
      />
    </>
  );
};
