import type React from "react";

import { useNavigate } from "@tanstack/react-router";

import { Button, PageSection } from "@patternfly/react-core";
import CheckCircleIcon from "@patternfly/react-icons/dist/esm/icons/check-circle-icon";

import { DocumentTitle } from "@app/components/DocumentTitle";
import { FullPageEmptyState } from "@app/components/FullPageEmptyState";

/**
 * Where Keycloak returns the user after logging out (ADR 0003): a public page, so it never
 * starts a new login by itself; the user signs in again only by choice.
 */
export const SessionClosed: React.FC = () => {
  const navigate = useNavigate();

  return (
    <>
      <DocumentTitle title="Sesión cerrada" />
      <PageSection>
        <FullPageEmptyState
          titleText="Sesión cerrada"
          icon={CheckCircleIcon}
          body="Cerraste tu sesión correctamente. Puedes cerrar esta ventana o volver a iniciar sesión."
          actions={
            <Button
              variant="primary"
              onClick={() => void navigate({ to: "/" })}
            >
              Iniciar sesión
            </Button>
          }
        />
      </PageSection>
    </>
  );
};
