import type React from "react";

import { Content, ContentVariants } from "@patternfly/react-core";

import type { IMutationErrorMessage } from "@app/utils/apiError";

interface IApiErrorTextProps {
  error: IMutationErrorMessage;
}

/** The server's message, then its detail in small print; the error's `kind` is never shown. */
export const ApiErrorText: React.FC<IApiErrorTextProps> = ({ error }) => (
  <>
    {error.description ? (
      <Content component={ContentVariants.p}>{error.description}</Content>
    ) : null}
    {error.detail ? (
      <Content component={ContentVariants.small}>{error.detail}</Content>
    ) : null}
  </>
);
