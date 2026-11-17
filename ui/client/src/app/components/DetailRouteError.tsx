import type React from "react";

import type { ErrorComponentProps } from "@tanstack/react-router";

import { PageSection } from "@patternfly/react-core";
import ExclamationCircleIcon from "@patternfly/react-icons/dist/esm/icons/exclamation-circle-icon";
import LockIcon from "@patternfly/react-icons/dist/esm/icons/lock-icon";
import PathMissingIcon from "@patternfly/react-icons/dist/esm/icons/path-missing-icon";

import { FullPageEmptyState } from "@app/components/FullPageEmptyState";
import { isForbiddenError, isNotFoundError } from "@app/utils/isHttpError";

/** Shared route `errorComponent` for detail pages: 403 / 404 / generic states (foreign and unknown tenants are both 404). */
export const DetailRouteError: React.FC<ErrorComponentProps> = ({ error }) => {
  if (isForbiddenError(error)) {
    return (
      <PageSection>
        <FullPageEmptyState
          titleText="403: Acceso denegado"
          icon={LockIcon}
          body="No tienes permiso para ver esta página. Si crees que es un error, contacta a tu administrador."
        />
      </PageSection>
    );
  }

  if (isNotFoundError(error)) {
    return (
      <PageSection>
        <FullPageEmptyState
          titleText="404: La página no existe"
          icon={PathMissingIcon}
          body="El recurso que buscas no existe o no tienes acceso a él."
        />
      </PageSection>
    );
  }

  return (
    <PageSection>
      <FullPageEmptyState
        titleText="No se pudo cargar el recurso"
        icon={ExclamationCircleIcon}
        status="danger"
        body="Ocurrió un error al cargar esta página. Inténtalo más tarde."
      />
    </PageSection>
  );
};
