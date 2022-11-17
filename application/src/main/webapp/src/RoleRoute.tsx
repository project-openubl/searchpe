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

export interface IRoleRouteProps {
  hasAny?: Permission[];
  children: React.ReactElement;
}

export const RoleRoute: React.FC<IRoleRouteProps> = ({ hasAny, children }) => {
  const { isAllowed } = usePermission({ hasAny: hasAny || [] });

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

  return !isAllowed && hasAny ? notAuthorizedState : children;
};
