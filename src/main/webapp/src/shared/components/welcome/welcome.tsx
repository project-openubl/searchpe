import React from "react";
import {
  EmptyState,
  Title,
  EmptyStateBody,
  Button,
  EmptyStateVariant,
  EmptyStateSecondaryActions,
  EmptyStateIcon,
} from "@patternfly/react-core";
import { RocketIcon } from "@patternfly/react-icons";

export interface WelcomeProps {
  onPrimaryAction: () => void;
}

export const Welcome: React.FC<WelcomeProps> = ({ onPrimaryAction }) => {
  return (
    <EmptyState variant={EmptyStateVariant.small}>
      <EmptyStateIcon icon={RocketIcon} />
      <Title headingLevel="h4" size="lg">
        Welcome to Searchpe
      </Title>
      <EmptyStateBody>
        Searchpe helps you to consume the data exposed by the SUNAT through the
        'padrón reducido'. To start searching data you first need to have at
        least one valid <strong>Version</strong>
      </EmptyStateBody>
      <Button variant="primary" onClick={onPrimaryAction}>
        Go to Versions
      </Button>
      <EmptyStateSecondaryActions>
        To learn more, visit the
        <a
          target="_blank"
          href="https://project-openubl.github.io"
          rel="noopener noreferrer"
        >
          documentation.
        </a>
      </EmptyStateSecondaryActions>
    </EmptyState>
  );
};
