import React from "react";
import {
  EmptyState,
  EmptyStateBody,
  EmptyStateIcon,
  EmptyStateVariant,
  Title,
} from "@patternfly/react-core";
import { AddCircleOIcon } from "@patternfly/react-icons";

export const StateNoData: React.FC = () => {
  return (
    <EmptyState variant={EmptyStateVariant.small}>
      <EmptyStateIcon icon={AddCircleOIcon} />
      <Title headingLevel="h2" size="lg">
        No data available
      </Title>
      <EmptyStateBody>
        Create a new resource to search using this table.
      </EmptyStateBody>
    </EmptyState>
  );
};
