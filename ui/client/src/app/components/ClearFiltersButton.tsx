import type React from "react";

import { Button, ToolbarItem } from "@patternfly/react-core";

interface IClearFiltersButtonProps {
  onClick: () => void;
}

/**
 * The toolbar's "clear filters" link in Spanish: pass it as `DataViewToolbar`'s
 * `customLabelGroupContent` (the built-in one reads "Clear filters").
 */
export const ClearFiltersButton: React.FC<IClearFiltersButtonProps> = ({
  onClick,
}) => (
  <ToolbarItem>
    <Button variant="link" onClick={onClick} isInline>
      Limpiar filtros
    </Button>
  </ToolbarItem>
);
