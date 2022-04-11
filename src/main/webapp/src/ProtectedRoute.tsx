import React from "react";

import {
  Bullseye,
  EmptyState,
  EmptyStateBody,
  EmptyStateIcon,
  EmptyStateVariant,
  Title,
} from "@patternfly/react-core";
import { WarningTriangleIcon } from "@patternfly/react-icons";

import { usePermission } from "shared/hooks";
import { Permission } from "Constants";

export interface IProtectedRouteProps {
  hasAny: Permission[];
  children: React.ReactElement;
}

export const ProtectedRoute: React.FC<IProtectedRouteProps> = ({
  hasAny,
  children,
}) => {
  const { isAllowed } = usePermission({ hasAny });

  const notAuthorizedState = (
    <Bullseye>
      <EmptyState variant={EmptyStateVariant.small}>
        <EmptyStateIcon icon={WarningTriangleIcon} />
        <Title headingLevel="h2" size="lg">
          403 Forbidden
        </Title>
        <EmptyStateBody>You are not allowed to access this page</EmptyStateBody>
      </EmptyState>
    </Bullseye>
  );

  return !isAllowed ? notAuthorizedState : children;
};
