import type React from "react";
import { useId, useState } from "react";
import { Controller, type FieldError, useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";

import {
  Button,
  Form,
  FormGroup,
  FormHelperText,
  FormSelect,
  FormSelectOption,
  HelperText,
  HelperTextItem,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
  TextArea,
  TextInput,
} from "@patternfly/react-core";

import { FormSubmitError } from "@app/components/FormSubmitError";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSubmitError } from "@app/hooks/useSubmitError";
import {
  DATA_SUBJECT_REQUEST_KINDS,
  useDataSubjectRequestCreateMutation,
} from "@app/queries/xhandler/data-subject-requests";
import { getMutationErrorMessage } from "@app/utils/apiError";

import {
  DATA_SUBJECT_REQUEST_KIND_LABELS,
  SUBJECT_DOC_TYPES,
} from "../../dataSubjectRequestPresentation";
import {
  createDataSubjectRequestSchema,
  type ICreateDataSubjectRequestValues,
} from "./createDataSubjectRequestSchema";

interface ICreateDataSubjectRequestModalProps {
  onClose: () => void;
}

const fieldError = (error: FieldError | undefined) =>
  error ? (
    <FormHelperText>
      <HelperText>
        <HelperTextItem variant="error">{error.message}</HelperTextItem>
      </HelperText>
    </FormHelperText>
  ) : null;

/** Records a personal-data request received by the tenant; its legal term starts now. */
export const CreateDataSubjectRequestModal: React.FC<
  ICreateDataSubjectRequestModalProps
> = ({ onClose }) => {
  const formId = useId();
  const titleId = useId();
  const [idempotencyKey] = useState(() => crypto.randomUUID());
  const { addNotification } = useNotifications();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();

  const {
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<ICreateDataSubjectRequestValues>({
    resolver: yupResolver(createDataSubjectRequestSchema),
    defaultValues: {
      subjectDocType: "1",
      subjectDocNumber: "",
      subjectName: "",
      kind: "access",
      details: "",
    },
  });

  const createRequest = useDataSubjectRequestCreateMutation({
    onSuccess: () =>
      addNotification({
        variant: "success",
        title: "Se registró la solicitud",
      }),
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: "No se pudo registrar la solicitud",
        }),
      ),
  });

  const onSubmit = (values: ICreateDataSubjectRequestValues) => {
    clearSubmitError();
    createRequest.mutate(
      {
        idempotencyKey,
        body: {
          subject_doc_type: values.subjectDocType,
          subject_doc_number: values.subjectDocNumber,
          subject_name: values.subjectName || undefined,
          kind: values.kind,
          details: values.details || undefined,
        },
      },
      { onSuccess: onClose },
    );
  };

  return (
    <Modal isOpen variant="medium" onClose={onClose} aria-labelledby={titleId}>
      <ModalHeader
        title="Nueva solicitud de datos personales"
        labelId={titleId}
        description="El plazo para responder empieza hoy: 20 días para acceso, 10 para los demás derechos."
      />
      <ModalBody>
        <Form id={formId} onSubmit={handleSubmit(onSubmit)}>
          <FormSubmitError error={submitError} />
          <Controller
            control={control}
            name="subjectDocType"
            render={({ field }) => (
              <FormGroup
                label="Tipo de documento"
                isRequired
                fieldId={`${formId}-doc-type`}
              >
                <FormSelect
                  id={`${formId}-doc-type`}
                  value={field.value}
                  onChange={(_event, value) => field.onChange(value)}
                >
                  {SUBJECT_DOC_TYPES.map((type) => (
                    <FormSelectOption
                      key={type.value}
                      value={type.value}
                      label={type.label}
                    />
                  ))}
                </FormSelect>
              </FormGroup>
            )}
          />
          <Controller
            control={control}
            name="subjectDocNumber"
            render={({ field }) => (
              <FormGroup
                label="Número de documento"
                isRequired
                fieldId={`${formId}-doc-number`}
              >
                <TextInput
                  id={`${formId}-doc-number`}
                  value={field.value}
                  onChange={(_event, value) => field.onChange(value)}
                  onBlur={field.onBlur}
                  validated={errors.subjectDocNumber ? "error" : "default"}
                />
                {fieldError(errors.subjectDocNumber)}
              </FormGroup>
            )}
          />
          <Controller
            control={control}
            name="subjectName"
            render={({ field }) => (
              <FormGroup label="Nombre del titular" fieldId={`${formId}-name`}>
                <TextInput
                  id={`${formId}-name`}
                  value={field.value}
                  onChange={(_event, value) => field.onChange(value)}
                  onBlur={field.onBlur}
                  validated={errors.subjectName ? "error" : "default"}
                />
                {fieldError(errors.subjectName)}
              </FormGroup>
            )}
          />
          <Controller
            control={control}
            name="kind"
            render={({ field }) => (
              <FormGroup label="Derecho" isRequired fieldId={`${formId}-kind`}>
                <FormSelect
                  id={`${formId}-kind`}
                  value={field.value}
                  onChange={(_event, value) => field.onChange(value)}
                >
                  {DATA_SUBJECT_REQUEST_KINDS.map((kind) => (
                    <FormSelectOption
                      key={kind}
                      value={kind}
                      label={DATA_SUBJECT_REQUEST_KIND_LABELS[kind]}
                    />
                  ))}
                </FormSelect>
              </FormGroup>
            )}
          />
          <Controller
            control={control}
            name="details"
            render={({ field }) => (
              <FormGroup label="Detalle" fieldId={`${formId}-details`}>
                <TextArea
                  id={`${formId}-details`}
                  value={field.value}
                  onChange={(_event, value) => field.onChange(value)}
                  onBlur={field.onBlur}
                  validated={errors.details ? "error" : "default"}
                  resizeOrientation="vertical"
                />
                {fieldError(errors.details)}
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
          isLoading={createRequest.isPending}
          isDisabled={createRequest.isPending}
        >
          Registrar
        </Button>
        <Button variant="link" onClick={onClose}>
          Cancelar
        </Button>
      </ModalFooter>
    </Modal>
  );
};
