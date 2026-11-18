import type React from "react";
import { useId, useState } from "react";
import { Controller, useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";

import {
  Button,
  Form,
  FormGroup,
  FormHelperText,
  HelperText,
  HelperTextItem,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
  TextInput,
} from "@patternfly/react-core";

import type { NubefactToken } from "@app/client/xhandler";
import { FormSubmitError } from "@app/components/FormSubmitError";
import { useSubmitError } from "@app/hooks/useSubmitError";
import { useNubefactTokenCreateMutation } from "@app/queries/xhandler/adapter-tokens";
import { getMutationErrorMessage } from "@app/utils/apiError";

import {
  createAdapterTokenSchema,
  type ICreateAdapterTokenValues,
} from "./createAdapterTokenSchema";

interface ICreateAdapterTokenModalProps {
  companyId: string;
  /** Receives the new token, shown once by the caller. */
  onCreated: (token: NubefactToken) => void;
  onClose: () => void;
}

/** Creates a Nubefact-compatible adapter token for the company's ERP. */
export const CreateAdapterTokenModal: React.FC<
  ICreateAdapterTokenModalProps
> = ({ companyId, onCreated, onClose }) => {
  const formId = useId();
  const titleId = useId();
  const [idempotencyKey] = useState(() => crypto.randomUUID());
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();

  const {
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<ICreateAdapterTokenValues>({
    resolver: yupResolver(createAdapterTokenSchema),
    defaultValues: { label: "" },
  });

  const createToken = useNubefactTokenCreateMutation({
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: "No se pudo crear el token",
        }),
      ),
  });

  const onSubmit = (values: ICreateAdapterTokenValues) => {
    clearSubmitError();
    createToken.mutate(
      { companyId, idempotencyKey, body: { label: values.label } },
      { onSuccess: onCreated },
    );
  };

  return (
    <Modal isOpen variant="small" onClose={onClose} aria-labelledby={titleId}>
      <ModalHeader title="Nuevo token de Nubefact" labelId={titleId} />
      <ModalBody>
        <Form id={formId} onSubmit={handleSubmit(onSubmit)}>
          <FormSubmitError error={submitError} />
          <Controller
            control={control}
            name="label"
            render={({ field }) => (
              <FormGroup
                label="Etiqueta"
                isRequired
                fieldId={`${formId}-label`}
              >
                <TextInput
                  id={`${formId}-label`}
                  value={field.value}
                  onChange={(_event, value) => field.onChange(value)}
                  onBlur={field.onBlur}
                  validated={errors.label ? "error" : "default"}
                  placeholder="ERP de la tienda principal"
                />
                <FormHelperText>
                  <HelperText>
                    {errors.label ? (
                      <HelperTextItem variant="error">
                        {errors.label.message}
                      </HelperTextItem>
                    ) : (
                      <HelperTextItem>
                        Para reconocer qué sistema usa el token.
                      </HelperTextItem>
                    )}
                  </HelperText>
                </FormHelperText>
              </FormGroup>
            )}
          />
        </Form>
      </ModalBody>
      <ModalFooter>
        <Button
          type="submit"
          form={formId}
          variant="primary"
          isLoading={createToken.isPending}
          isDisabled={createToken.isPending}
        >
          Crear
        </Button>
        <Button variant="link" onClick={onClose}>
          Cancelar
        </Button>
      </ModalFooter>
    </Modal>
  );
};
