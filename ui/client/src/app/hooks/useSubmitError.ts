import { useState } from "react";

import type { IMutationErrorMessage } from "@app/utils/apiError";

export interface IUseSubmitErrorResult {
  /** The current submission error, or null when there is none. */
  submitError: IMutationErrorMessage | null;
  /** Record a submission failure (built with `getMutationErrorMessage`). */
  setSubmitError: (error: IMutationErrorMessage) => void;
  /** Clear the error — call at the start of each submit so a retry resets it. */
  clearSubmitError: () => void;
}

/**
 * State for a modal form's submission error, shown in a top-of-form `FormSubmitError`
 * instead of a toast (see `.rulesync/rules/forms.md`).
 */
export const useSubmitError = (): IUseSubmitErrorResult => {
  const [submitError, setSubmitError] = useState<IMutationErrorMessage | null>(
    null,
  );
  const clearSubmitError = () => setSubmitError(null);
  return { submitError, setSubmitError, clearSubmitError };
};
