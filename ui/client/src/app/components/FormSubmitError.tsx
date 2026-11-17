import type React from "react";

import { Alert, FormAlert } from "@patternfly/react-core";

import { ApiErrorText } from "@app/components/ApiErrorText";
import type { IMutationErrorMessage } from "@app/utils/apiError";

interface IFormSubmitErrorProps {
  /** The submission error to show, or null to render nothing. */
  error: IMutationErrorMessage | null;
}

/**
 * Top-of-form alert for a form's submission failure, rendered as the first child of
 * a `<Form>` (see `.rulesync/rules/forms.md`).
 */
export const FormSubmitError: React.FC<IFormSubmitErrorProps> = ({ error }) => {
  if (!error) return null;
  return (
    <FormAlert>
      <Alert variant="danger" isInline aria-live="polite" title={error.title}>
        <ApiErrorText error={error} />
      </Alert>
    </FormAlert>
  );
};
