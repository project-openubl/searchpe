import type React from "react";

import {
  Button,
  EmptyState,
  EmptyStateActions,
  EmptyStateBody,
  EmptyStateFooter,
} from "@patternfly/react-core";
import CubesIcon from "@patternfly/react-icons/dist/esm/icons/cubes-icon";
import SearchIcon from "@patternfly/react-icons/dist/esm/icons/search-icon";

interface ITableEmptyStateProps {
  title: string;
  body?: React.ReactNode;
  icon?: React.ComponentType;
  /** Render the "no matches for the current filter" variant. */
  isFiltered?: boolean;
  onClearFilters?: () => void;
  filteredTitle?: string;
  filteredBody?: React.ReactNode;
}

/** Pass as the `emptyState` node to `dataViewBodyStates`. */
export const TableEmptyState: React.FC<ITableEmptyStateProps> = ({
  title,
  body,
  icon,
  isFiltered,
  onClearFilters,
  filteredTitle = "No se encontraron resultados",
  filteredBody = "Ningún resultado coincide con los filtros. Cambia o limpia los filtros.",
}) => {
  if (isFiltered) {
    return (
      <EmptyState
        variant="sm"
        icon={SearchIcon}
        titleText={filteredTitle}
        headingLevel="h4"
      >
        <EmptyStateBody>{filteredBody}</EmptyStateBody>
        {onClearFilters && (
          <EmptyStateFooter>
            <EmptyStateActions>
              <Button variant="link" onClick={onClearFilters}>
                Limpiar filtros
              </Button>
            </EmptyStateActions>
          </EmptyStateFooter>
        )}
      </EmptyState>
    );
  }

  return (
    <EmptyState
      variant="sm"
      icon={icon ?? CubesIcon}
      titleText={title}
      headingLevel="h4"
    >
      {body ? <EmptyStateBody>{body}</EmptyStateBody> : null}
    </EmptyState>
  );
};
