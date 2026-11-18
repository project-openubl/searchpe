import type React from "react";
import { useId } from "react";

import {
  Card,
  CardBody,
  Content,
  ContentVariants,
  PageSection,
  Stack,
  StackItem,
} from "@patternfly/react-core";

import { DocumentTitle } from "@app/components/DocumentTitle";
import { useSubmitError } from "@app/hooks/useSubmitError";
import { useOseLookupMutation } from "@app/queries/xhandler/public-lookup";
import { getMutationErrorMessage } from "@app/utils/apiError";

import { OseLookupForm } from "./components/OseLookupForm";
import { OseLookupResultAlert } from "./components/OseLookupResultAlert";

/**
 * The OSE validity page (RS 242-2018, DESIGN.md §12): anyone checks whether a comprobante got a
 * CDR from the OSE, without a session and through the token-less client (ADR 0003).
 */
export const ConsultaValidez: React.FC = () => {
  const formId = useId();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();
  const lookup = useOseLookupMutation({
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: "No se pudo consultar el comprobante",
        }),
      ),
  });

  return (
    <>
      <DocumentTitle title="Consulta de validez" />
      <PageSection>
        <Content component={ContentVariants.h1}>
          Consulta de validez de comprobantes
        </Content>
        <Content component={ContentVariants.p}>
          Verifica si un comprobante electrónico recibido por el OSE es válido.
          No necesitas una cuenta.
        </Content>
      </PageSection>
      <PageSection>
        <Stack hasGutter>
          <StackItem>
            <Card>
              <CardBody>
                <OseLookupForm
                  formId={formId}
                  submitError={submitError}
                  isSubmitting={lookup.isPending}
                  onSubmit={(request) => {
                    clearSubmitError();
                    lookup.reset();
                    lookup.mutate(request);
                  }}
                />
              </CardBody>
            </Card>
          </StackItem>
          {lookup.data ? (
            <StackItem>
              <OseLookupResultAlert result={lookup.data} />
            </StackItem>
          ) : null}
        </Stack>
      </PageSection>
    </>
  );
};
