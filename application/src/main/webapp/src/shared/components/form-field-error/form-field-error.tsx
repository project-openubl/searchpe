import React from "react";
import {
  FormHelperText,
  HelperText,
  HelperTextItem,
} from "@patternfly/react-core";

export interface FormFieldErrorProps {
  /** Error message; nothing is rendered when empty */
  message?: string;
}

/**
 * Replacement for PatternFly 4's `FormGroup helperTextInvalid` prop,
 * which was removed in PatternFly 5/6.
 */
export const FormFieldError: React.FC<FormFieldErrorProps> = ({ message }) => {
  if (!message) {
    return null;
  }

  return (
    <FormHelperText>
      <HelperText>
        <HelperTextItem variant="error">{message}</HelperTextItem>
      </HelperText>
    </FormHelperText>
  );
};
