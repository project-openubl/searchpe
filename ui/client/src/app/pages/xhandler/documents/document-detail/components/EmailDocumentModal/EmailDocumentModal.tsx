import type React from "react";
import { useId } from "react";
import { Controller, useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";

import {
  Button,
  Checkbox,
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

import type { DocumentView } from "@app/client/xhandler";
import { FormSubmitError } from "@app/components/FormSubmitError";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSubmitError } from "@app/hooks/useSubmitError";
import { useDocumentEmailMutation } from "@app/queries/xhandler/documents";
import { getMutationErrorMessage } from "@app/utils/apiError";

import {
  emailDocumentSchema,
  type IEmailDocumentValues,
  splitAddresses,
} from "./emailDocumentSchema";

interface IEmailDocumentModalProps {
  document: DocumentView;
  onClose: () => void;
}

/** Emails the document (PDF, XML and optionally the CDR) with the buyer's link: a job sends it. */
export const EmailDocumentModal: React.FC<IEmailDocumentModalProps> = ({
  document,
  onClose,
}) => {
  const formId = useId();
  const titleId = useId();
  const { addNotification } = useNotifications();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();

  const {
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<IEmailDocumentValues>({
    resolver: yupResolver(emailDocumentSchema),
    defaultValues: { to: "", cc: "", includeCdr: false },
  });

  const emailDocument = useDocumentEmailMutation({
    onSuccess: (_data, { body }) =>
      addNotification({
        variant: "info",
        title: "Se programó el envío del correo",
        description: `Un trabajo lo enviará a ${body.to.join(", ")}.`,
      }),
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: "No se pudo enviar el correo",
        }),
      ),
  });

  const onSubmit = (values: IEmailDocumentValues) => {
    clearSubmitError();
    const cc = splitAddresses(values.cc);
    emailDocument.mutate(
      {
        documentId: document.id ?? "",
        body: {
          to: splitAddresses(values.to),
          cc: cc.length > 0 ? cc : undefined,
          include_cdr: values.includeCdr,
        },
      },
      { onSuccess: onClose },
    );
  };

  const fieldError = (message: string | undefined) =>
    message ? (
      <FormHelperText>
        <HelperText>
          <HelperTextItem variant="error">{message}</HelperTextItem>
        </HelperText>
      </FormHelperText>
    ) : null;

  return (
    <Modal isOpen variant="small" onClose={onClose} aria-labelledby={titleId}>
      <ModalHeader
        title={`Enviar ${document.serie_numero ?? "comprobante"} por correo`}
        labelId={titleId}
      />
      <ModalBody>
        <Form id={formId} onSubmit={handleSubmit(onSubmit)}>
          <FormSubmitError error={submitError} />
          <Controller
            control={control}
            name="to"
            render={({ field }) => (
              <FormGroup label="Para" isRequired fieldId={`${formId}-to`}>
                <TextInput
                  id={`${formId}-to`}
                  type="text"
                  value={field.value}
                  onChange={(_event, value) => field.onChange(value)}
                  onBlur={field.onBlur}
                  validated={errors.to ? "error" : "default"}
                  placeholder="compras@cliente.pe"
                />
                {errors.to ? (
                  fieldError(errors.to.message)
                ) : (
                  <FormHelperText>
                    <HelperText>
                      <HelperTextItem>
                        Separa varios correos con comas.
                      </HelperTextItem>
                    </HelperText>
                  </FormHelperText>
                )}
              </FormGroup>
            )}
          />
          <Controller
            control={control}
            name="cc"
            render={({ field }) => (
              <FormGroup label="Con copia" fieldId={`${formId}-cc`}>
                <TextInput
                  id={`${formId}-cc`}
                  type="text"
                  value={field.value}
                  onChange={(_event, value) => field.onChange(value)}
                  onBlur={field.onBlur}
                  validated={errors.cc ? "error" : "default"}
                />
                {fieldError(errors.cc?.message)}
              </FormGroup>
            )}
          />
          <Controller
            control={control}
            name="includeCdr"
            render={({ field }) => (
              <FormGroup fieldId={`${formId}-cdr`}>
                <Checkbox
                  id={`${formId}-cdr`}
                  label="Adjuntar el CDR de SUNAT"
                  isChecked={field.value}
                  onChange={(_event, checked) => field.onChange(checked)}
                />
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
          isLoading={emailDocument.isPending}
          isDisabled={emailDocument.isPending}
        >
          Enviar
        </Button>
        <Button variant="link" onClick={onClose}>
          Cancelar
        </Button>
      </ModalFooter>
    </Modal>
  );
};
