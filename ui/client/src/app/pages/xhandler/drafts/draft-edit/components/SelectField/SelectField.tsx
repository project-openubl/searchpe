import type React from "react";
import { useId } from "react";
import {
  type Control,
  type FieldPath,
  type FieldValues,
  useController,
} from "react-hook-form";

import {
  FormGroup,
  FormHelperText,
  FormSelect,
  FormSelectOption,
  HelperText,
  HelperTextItem,
} from "@patternfly/react-core";

interface ISelectFieldProps<T extends FieldValues> {
  control: Control<T>;
  name: FieldPath<T>;
  label: string;
  options: readonly { value: string; label: string }[];
  isRequired?: boolean;
  helperText?: React.ReactNode;
  isDisabled?: boolean;
}

/** A react-hook-form `FormSelect` with its label and yup message (forms rule). */
export const SelectField = <T extends FieldValues>({
  control,
  name,
  label,
  options,
  isRequired,
  helperText,
  isDisabled,
}: ISelectFieldProps<T>): React.ReactElement => {
  const id = useId();
  const { field, fieldState } = useController({ control, name });
  const error = fieldState.error?.message;

  return (
    <FormGroup label={label} isRequired={isRequired} fieldId={id}>
      <FormSelect
        id={id}
        name={field.name}
        value={(field.value as string | undefined) ?? ""}
        onChange={(_event, next) => field.onChange(next)}
        onBlur={field.onBlur}
        validated={error ? "error" : "default"}
        isDisabled={isDisabled}
        aria-invalid={!!error}
      >
        {options.map((option) => (
          <FormSelectOption
            key={option.value}
            value={option.value}
            label={option.label}
          />
        ))}
      </FormSelect>
      {error || helperText ? (
        <FormHelperText>
          <HelperText>
            <HelperTextItem variant={error ? "error" : "default"}>
              {error ?? helperText}
            </HelperTextItem>
          </HelperText>
        </FormHelperText>
      ) : null}
    </FormGroup>
  );
};
