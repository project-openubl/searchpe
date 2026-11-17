import React, { useState } from "react";
import {
  FieldValues,
  useController,
  UseControllerProps,
} from "react-hook-form";
import {
  Badge,
  MenuToggle,
  MenuToggleElement,
  Select,
  SelectList,
  SelectOption,
} from "@patternfly/react-core";

export interface IControllerSelectMultipleProps<
  TFieldValues extends FieldValues,
> {
  fieldConfig: UseControllerProps<TFieldValues>;
  selectConfig: {
    "aria-label": string;
    "aria-describedby"?: string;
    placeholderText?: string;
    isDisabled?: boolean;
  };
  options: string[];
  isEqual?: (a: string, b: string) => boolean;
}

/**
 * Multi (checkbox) select bound to a react-hook-form field holding a string[].
 * Built on the PatternFly 6 composable Select (replaces lib-ui's SimpleSelect).
 */
export const ControllerSelectMultiple = <TFieldValues extends FieldValues>({
  fieldConfig,
  selectConfig,
  options,
  isEqual = (a, b) => a === b,
}: IControllerSelectMultipleProps<TFieldValues>) => {
  const { field } = useController(fieldConfig);
  const [isOpen, setIsOpen] = useState(false);

  const currentValue: string[] = field.value || [];

  const onSelect = (selection: string) => {
    const elementExists = currentValue.some((f) => isEqual(f, selection));
    const nextValue = elementExists
      ? currentValue.filter((f) => !isEqual(f, selection))
      : [...currentValue, selection];
    field.onChange(nextValue);
  };

  const toggle = (toggleRef: React.Ref<MenuToggleElement>) => (
    <MenuToggle
      ref={toggleRef}
      onClick={() => setIsOpen((current) => !current)}
      isExpanded={isOpen}
      isDisabled={selectConfig.isDisabled}
      isFullWidth
      aria-label={selectConfig["aria-label"]}
      aria-describedby={selectConfig["aria-describedby"]}
      badge={
        currentValue.length > 0 ? (
          <Badge isRead>{currentValue.length}</Badge>
        ) : undefined
      }
    >
      {selectConfig.placeholderText ?? "Select..."}
    </MenuToggle>
  );

  return (
    <Select
      role="menu"
      isOpen={isOpen}
      selected={currentValue}
      onSelect={(_event, selection) => onSelect(selection as string)}
      onOpenChange={(nextOpen) => {
        setIsOpen(nextOpen);
        if (!nextOpen) {
          field.onBlur();
        }
      }}
      toggle={toggle}
    >
      <SelectList>
        {options.map((option) => (
          <SelectOption
            key={option}
            value={option}
            hasCheckbox
            isSelected={currentValue.some((f) => isEqual(f, option))}
          >
            {option}
          </SelectOption>
        ))}
      </SelectList>
    </Select>
  );
};
