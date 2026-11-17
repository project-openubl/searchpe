import type React from "react";
import { type Ref, useState } from "react";

import {
  MenuToggle,
  type MenuToggleElement,
  Select,
  SelectList,
  SelectOption,
  ToolbarFilter,
  type ToolbarLabel,
} from "@patternfly/react-core";
import FilterIcon from "@patternfly/react-icons/dist/esm/icons/filter-icon";

export interface ISingleSelectOption {
  label: string;
  value: string;
}

/** Stable empty default so the component doesn't get a new array each render. */
const NO_SELECTION: string[] = [];

interface ISingleSelectFilterProps {
  options: ISingleSelectOption[];
  /** The toggle's text (and accessible name) while nothing is picked. */
  placeholder: string;
  // --- Identity read by DataViewFilters (must be passed on the element) ---
  filterId: string;
  title: string;
  // --- Injected by DataViewFilters via cloneElement ---
  /** A list of at most one value, like `DataViewCheckboxFilter`'s, so URL state stays a list. */
  value?: string[];
  onChange?: (event: unknown, value: string[]) => void;
  showToolbarItem?: boolean;
}

/**
 * A one-value filter for `DataViewFilters`, for APIs that filter by a single value: picking an
 * option replaces the previous one, picking it again clears it. Its `ToolbarFilter` lets
 * DataView manage the chip.
 */
export const SingleSelectFilter: React.FC<ISingleSelectFilterProps> = ({
  options,
  placeholder,
  filterId,
  title,
  value = NO_SELECTION,
  onChange,
  showToolbarItem,
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const selected = value[0];
  const selectedLabel = options.find(
    (option) => option.value === selected,
  )?.label;

  const labels: ToolbarLabel[] = selected
    ? [{ key: selected, node: selectedLabel ?? selected }]
    : [];

  return (
    <ToolbarFilter
      labels={labels}
      deleteLabel={() => onChange?.(undefined, [])}
      categoryName={title}
      showToolbarItem={showToolbarItem}
    >
      <Select
        isOpen={isOpen}
        selected={selected}
        onOpenChange={setIsOpen}
        onSelect={(_event, picked) => {
          const next = String(picked ?? "");
          onChange?.(undefined, next && next !== selected ? [next] : []);
          setIsOpen(false);
        }}
        toggle={(toggleRef: Ref<MenuToggleElement>) => (
          <MenuToggle
            ref={toggleRef}
            id={`${filterId}-filter-toggle`}
            aria-label={placeholder}
            icon={<FilterIcon />}
            onClick={() => setIsOpen((open) => !open)}
            isExpanded={isOpen}
          >
            {selectedLabel ?? placeholder}
          </MenuToggle>
        )}
      >
        <SelectList>
          {options.map((option) => (
            <SelectOption
              key={option.value}
              value={option.value}
              isSelected={option.value === selected}
            >
              {option.label}
            </SelectOption>
          ))}
        </SelectList>
      </Select>
    </ToolbarFilter>
  );
};
