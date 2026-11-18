import type React from "react";
import { useId } from "react";
import { Controller, useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";

import {
  Button,
  Form,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
  Switch,
} from "@patternfly/react-core";

import { ControlledTextField } from "@app/components/ControlledTextField";
import { FormSubmitError } from "@app/components/FormSubmitError";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSubmitError } from "@app/hooks/useSubmitError";
import {
  type CredentialKind,
  useCredentialSetMutation,
} from "@app/queries/xhandler/credentials";
import { getMutationErrorMessage } from "@app/utils/apiError";

import {
  CREDENTIAL_PRESENTATION,
  type CredentialField,
  credentialSchema,
  EMPTY_CREDENTIAL_VALUES,
  type ICredentialFormValues,
  toCredentialWrite,
} from "./credentialForms";

interface ICredentialModalProps {
  /** The kind being set, or null while closed. */
  kind: CredentialKind | null;
  companyId: string;
  onClose: () => void;
}

/** Sets one credential kind; write-only, so the form never shows what is stored. */
export const CredentialModal: React.FC<ICredentialModalProps> = ({
  kind,
  companyId,
  onClose,
}) => (
  <Modal
    isOpen={kind !== null}
    onClose={onClose}
    variant="small"
    aria-labelledby="credential-modal-title"
  >
    {kind ? (
      <CredentialForm
        key={kind}
        kind={kind}
        companyId={companyId}
        onClose={onClose}
      />
    ) : null}
  </Modal>
);

interface ICredentialFormProps {
  kind: CredentialKind;
  companyId: string;
  onClose: () => void;
}

const CredentialForm: React.FC<ICredentialFormProps> = ({
  kind,
  companyId,
  onClose,
}) => {
  const formId = useId();
  const presentation = CREDENTIAL_PRESENTATION[kind];
  const { addNotification } = useNotifications();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();
  const { control, handleSubmit } = useForm<ICredentialFormValues>({
    resolver: yupResolver(credentialSchema(kind)),
    defaultValues: EMPTY_CREDENTIAL_VALUES,
    mode: "onTouched",
  });

  const save = useCredentialSetMutation(companyId, {
    // Built from the kind fixed for this form's lifetime, never from the secret.
    onSuccess: () =>
      addNotification({
        variant: "success",
        title: `Se guardó: ${presentation.title}`,
      }),
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: `No se pudo guardar: ${presentation.title}`,
        }),
      ),
  });

  const onSubmit = handleSubmit((values) => {
    clearSubmitError();
    save.mutate(toCredentialWrite({ kind, values }), { onSuccess: onClose });
  });

  const fieldNames = Object.keys(presentation.fields) as CredentialField[];

  return (
    <>
      <ModalHeader
        title={presentation.title}
        labelId="credential-modal-title"
        description={presentation.description}
      />
      <ModalBody>
        <Form id={formId} onSubmit={onSubmit} autoComplete="off">
          <FormSubmitError error={submitError} />
          {fieldNames.map((name) => {
            const field = presentation.fields[name];
            return field ? (
              <ControlledTextField
                key={name}
                control={control}
                name={name}
                label={field.label}
                isRequired={field.isRequired}
                helperText={field.helperText}
                type={field.isSecret ? "password" : "text"}
                autoComplete={field.isSecret ? "new-password" : "off"}
                inputMode={name === "port" ? "numeric" : undefined}
              />
            ) : null;
          })}
          {presentation.hasStartTls ? (
            <Controller
              control={control}
              name="start_tls"
              render={({ field }) => (
                <Switch
                  id={`${formId}-start-tls`}
                  label="Usar STARTTLS"
                  isChecked={field.value}
                  onChange={(_event, checked) => field.onChange(checked)}
                />
              )}
            />
          ) : null}
        </Form>
      </ModalBody>
      <ModalFooter>
        <Button
          type="submit"
          form={formId}
          isLoading={save.isPending}
          isDisabled={save.isPending}
        >
          Guardar
        </Button>
        <Button variant="link" onClick={onClose}>
          Cancelar
        </Button>
      </ModalFooter>
    </>
  );
};
