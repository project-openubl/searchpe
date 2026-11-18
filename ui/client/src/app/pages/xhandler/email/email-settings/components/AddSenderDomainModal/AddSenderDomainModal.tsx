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

import { FormSubmitError } from "@app/components/FormSubmitError";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSubmitError } from "@app/hooks/useSubmitError";
import { useSenderDomainAddMutation } from "@app/queries/xhandler/email";
import { getMutationErrorMessage } from "@app/utils/apiError";

import {
  addSenderDomainSchema,
  type IAddSenderDomainValues,
} from "./addSenderDomainSchema";

interface IAddSenderDomainModalProps {
  onClose: () => void;
}

/** Adds a sender domain; the table then shows the TXT record to publish. */
export const AddSenderDomainModal: React.FC<IAddSenderDomainModalProps> = ({
  onClose,
}) => {
  const formId = useId();
  const titleId = useId();
  const [idempotencyKey] = useState(() => crypto.randomUUID());
  const { addNotification } = useNotifications();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();

  const {
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<IAddSenderDomainValues>({
    resolver: yupResolver(addSenderDomainSchema),
    defaultValues: { domain: "" },
  });

  const addDomain = useSenderDomainAddMutation({
    onSuccess: (domain) =>
      addNotification({
        variant: "success",
        title: `Se agregó el dominio ${domain.domain ?? ""}`.trim(),
        description: "Publica el registro TXT y luego verifica el dominio.",
      }),
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: "No se pudo agregar el dominio",
        }),
      ),
  });

  const onSubmit = (values: IAddSenderDomainValues) => {
    clearSubmitError();
    addDomain.mutate(
      { domain: values.domain, idempotencyKey },
      { onSuccess: onClose },
    );
  };

  return (
    <Modal isOpen variant="small" onClose={onClose} aria-labelledby={titleId}>
      <ModalHeader title="Nuevo dominio remitente" labelId={titleId} />
      <ModalBody>
        <Form id={formId} onSubmit={handleSubmit(onSubmit)}>
          <FormSubmitError error={submitError} />
          <Controller
            control={control}
            name="domain"
            render={({ field }) => (
              <FormGroup
                label="Dominio"
                isRequired
                fieldId={`${formId}-domain`}
              >
                <TextInput
                  id={`${formId}-domain`}
                  value={field.value}
                  onChange={(_event, value) => field.onChange(value)}
                  onBlur={field.onBlur}
                  validated={errors.domain ? "error" : "default"}
                  placeholder="empresa.com.pe"
                />
                <FormHelperText>
                  <HelperText>
                    {errors.domain ? (
                      <HelperTextItem variant="error">
                        {errors.domain.message}
                      </HelperTextItem>
                    ) : (
                      <HelperTextItem>
                        Con el dominio verificado, los correos salen con la
                        dirección de la empresa.
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
          isLoading={addDomain.isPending}
          isDisabled={addDomain.isPending}
        >
          Agregar
        </Button>
        <Button variant="link" onClick={onClose}>
          Cancelar
        </Button>
      </ModalFooter>
    </Modal>
  );
};
