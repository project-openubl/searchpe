import { useController, UseControllerProps } from "react-hook-form";
import { SelectOptionObject } from "@patternfly/react-core";

import {
  ISimpleSelectProps,
  OptionWithValue,
  SimpleSelect,
} from "@project-openubl/lib-ui";

type OptionLike = string | SelectOptionObject | OptionWithValue;

export interface IControllerSelectMultipleProps {
  fieldConfig: UseControllerProps<any>;
  selectConfig: Omit<
    ISimpleSelectProps,
    "value" | "options" | "onChange" | "onClear"
  >;
  options: OptionLike[];
  isEqual?: (a: OptionLike, b: OptionLike) => boolean;
}

export const ControllerSelectMultiple = ({
  fieldConfig,
  selectConfig,
  options,
  isEqual = (a, b) => a === b,
}: IControllerSelectMultipleProps) => {
  const { field } = useController(fieldConfig);

  return (
    <SimpleSelect
      value={field.value}
      options={options}
      onChange={(selection) => {
        let selectionValue: any;

        if (typeof selection === "string") {
          selectionValue = selection;
        } else {
          const optionWithValue = selection as OptionWithValue;
          if (
            optionWithValue.value !== null &&
            optionWithValue.value !== undefined
          ) {
            selectionValue = optionWithValue;
          } else {
            selectionValue = selection;
          }
        }

        const currentValue = field.value;

        let nextValue: OptionLike[];
        const elementExists = currentValue.find((f: OptionLike) => {
          return isEqual(f, selectionValue);
        });

        if (elementExists) {
          nextValue = currentValue.filter(
            (f: OptionLike) => !isEqual(f, selectionValue)
          );
        } else {
          nextValue = [...currentValue, selectionValue];
        }

        field.onChange(nextValue);
      }}
      onClear={() => field.onChange([])}
      {...selectConfig}
    />
  );
};
