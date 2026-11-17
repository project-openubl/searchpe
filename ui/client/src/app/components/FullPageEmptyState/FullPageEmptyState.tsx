import type React from "react";

import {
  EmptyState,
  EmptyStateActions,
  EmptyStateBody,
  EmptyStateFooter,
  EmptyStateVariant,
} from "@patternfly/react-core";

interface IFullPageEmptyStateProps {
  titleText: string;
  body: React.ReactNode;
  icon: React.ComponentType;
  /** Set for error states so the icon renders in the danger (red) color. */
  status?: "danger";
  headingLevel?: "h1" | "h2" | "h4";
  variant?: EmptyStateVariant;
  /** Footer actions. Only the router crash boundary supplies these. */
  actions?: React.ReactNode;
}

/**
 * Full-page empty/error state (crash boundary, 404, 403, status/auth failures).
 * Callers keep their own `PageSection` / `Bullseye` wrapper.
 */
export const FullPageEmptyState: React.FC<IFullPageEmptyStateProps> = ({
  titleText,
  body,
  icon,
  status,
  headingLevel = "h1",
  variant = EmptyStateVariant.lg,
  actions,
}) => (
  <EmptyState
    titleText={titleText}
    headingLevel={headingLevel}
    icon={icon}
    status={status}
    variant={variant}
  >
    <EmptyStateBody>{body}</EmptyStateBody>
    {actions ? (
      <EmptyStateFooter>
        <EmptyStateActions>{actions}</EmptyStateActions>
      </EmptyStateFooter>
    ) : null}
  </EmptyState>
);
