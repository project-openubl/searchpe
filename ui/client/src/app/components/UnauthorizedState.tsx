import type React from "react";

import { EmptyState, EmptyStateBody } from "@patternfly/react-core";
import LockIcon from "@patternfly/react-icons/dist/esm/icons/lock-icon";

interface IUnauthorizedStateProps {
  /** What the user can't do here; defaults to viewing the page. */
  description?: string;
}

export const UnauthorizedState: React.FC<IUnauthorizedStateProps> = ({
  description = "No tienes permiso para ver esta página.",
}) => {
  return (
    <EmptyState
      titleText="403: Acceso denegado"
      headingLevel="h4"
      icon={LockIcon}
    >
      <EmptyStateBody>
        {description} Si crees que es un error, contacta a tu administrador.
      </EmptyStateBody>
    </EmptyState>
  );
};
