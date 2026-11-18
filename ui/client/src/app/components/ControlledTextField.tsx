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
  HelperText,
  HelperTextItem,
  TextArea,
  TextInput,
  type TextInputProps,
} from "@patternfly/react-core";

interface IControlledTextFieldProps<T extends FieldValues> {
  control: Control<T>;
  name: FieldPath<T>;
  label: string;
  isRequired?: boolean;
  /** Shown under the field while it has no error. */
  helperText?: React.ReactNode;
  type?: TextInputProps["type"];
  inputMode?: TextInputProps["inputMode"];
  autoComplete?: string;
  placeholder?: string;
  /** A multi-line `TextArea` instead of a `TextInput`. */
  isMultiline?: boolean;
  isDisabled?: boolean;
}

/**
 * A react-hook-form text field as a PatternFly `FormGroup`: label, input and the yup message
 * (forms rule). Generic over the form's values, so `name` is checked against them.
 */
export const ControlledTextField = <T extends FieldValues>({
  control,
  name,
  label,
  isRequired,
  helperText,
  type = "text",
  inputMode,
  autoComplete,
  placeholder,
  isMultiline,
  isDisabled,
}: IControlledTextFieldProps<T>): React.ReactElement => {
  const id = useId();
  const { field, fieldState } = useController({ control, name });
  const error = fieldState.error?.message;
  const validated = error ? "error" : "default";
  const value = (field.value as string | number | undefined) ?? "";

  return (
    <FormGroup label={label} isRequired={isRequired} fieldId={id}>
      {isMultiline ? (
        <TextArea
          id={id}
          name={field.name}
          value={String(value)}
          onChange={(_event, next) => field.onChange(next)}
          onBlur={field.onBlur}
          validated={validated}
          isDisabled={isDisabled}
          placeholder={placeholder}
          resizeOrientation="vertical"
          aria-invalid={!!error}
        />
      ) : (
        <TextInput
          id={id}
          name={field.name}
          ref={field.ref}
          type={type}
          value={value}
          onChange={(_event, next) => field.onChange(next)}
          onBlur={field.onBlur}
          validated={validated}
          isDisabled={isDisabled}
          inputMode={inputMode}
          autoComplete={autoComplete}
          placeholder={placeholder}
          aria-invalid={!!error}
        />
      )}
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
