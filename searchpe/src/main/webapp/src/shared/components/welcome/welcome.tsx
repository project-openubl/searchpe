import React from "react";
import {
  EmptyState,
  EmptyStateBody,
  Button,
  EmptyStateVariant,
  EmptyStateFooter,
  EmptyStateActions,
} from "@patternfly/react-core";
import { RocketIcon } from "@patternfly/react-icons";

export interface WelcomeProps {
  onPrimaryAction: () => void;
}

export const Welcome: React.FC<WelcomeProps> = ({ onPrimaryAction }) => {
  return (
    <EmptyState
      headingLevel="h4"
      icon={RocketIcon}
      titleText="Bienvenido a Searchpe"
      variant={EmptyStateVariant.sm}
    >
      <EmptyStateBody>
        Searchpe te ayuda a consumir los datos proveidos por la SUNAT a travéz
        del 'padrón reducido'. Antes de empezar a consultar datos, necesitas
        crear una <strong>Versión</strong>.
      </EmptyStateBody>
      <EmptyStateFooter>
        <Button variant="primary" onClick={onPrimaryAction}>
          Ir a Versiones
        </Button>
        <EmptyStateActions>
          Para aprender más visita la
          <a
            target="_blank"
            href="https://project-openubl.github.io"
            rel="noopener noreferrer"
          >
            documentación.
          </a>
        </EmptyStateActions>
      </EmptyStateFooter>
    </EmptyState>
  );
};
