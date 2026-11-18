import type React from "react";
import { useId } from "react";
import { Controller, useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";
import { type InferType, object, string } from "yup";

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
  TextArea,
} from "@patternfly/react-core";

import { FormSubmitError } from "@app/components/FormSubmitError";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSubmitError } from "@app/hooks/useSubmitError";
import {
  type IDataSubjectRequestRow,
  useDataSubjectRequestCloseMutation,
} from "@app/queries/xhandler/data-subject-requests";
import { getMutationErrorMessage } from "@app/utils/apiError";

const closeSchema = object({
  resolution: string()
    .trim()
    .required("Escribe cómo se atendió la solicitud.")
    .max(2000, "La respuesta admite hasta 2000 caracteres."),
});

type ICloseValues = InferType<typeof closeSchema>;

interface ICloseDataSubjectRequestModalProps {
  request: IDataSubjectRequestRow;
  onClose: () => void;
}

/** Closes an open request with how it was resolved. */
export const CloseDataSubjectRequestModal: React.FC<
  ICloseDataSubjectRequestModalProps
> = ({ request, onClose }) => {
  const formId = useId();
  const titleId = useId();
  const { addNotification } = useNotifications();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();

  const {
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<ICloseValues>({
    resolver: yupResolver(closeSchema),
    defaultValues: { resolution: "" },
  });

  const closeRequest = useDataSubjectRequestCloseMutation({
    onSuccess: () =>
      addNotification({ variant: "success", title: "Se cerró la solicitud" }),
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: "No se pudo cerrar la solicitud",
        }),
      ),
  });

  const onSubmit = (values: ICloseValues) => {
    clearSubmitError();
    closeRequest.mutate(
      { requestId: request.id, resolution: values.resolution },
      { onSuccess: onClose },
    );
  };

  return (
    <Modal isOpen variant="small" onClose={onClose} aria-labelledby={titleId}>
      <ModalHeader
        title={`Cerrar la solicitud de ${request.subjectName || request.subjectDocNumber}`}
        labelId={titleId}
      />
      <ModalBody>
        <Form id={formId} onSubmit={handleSubmit(onSubmit)}>
          <FormSubmitError error={submitError} />
          <Controller
            control={control}
            name="resolution"
            render={({ field }) => (
              <FormGroup
                label="Respuesta"
                isRequired
                fieldId={`${formId}-resolution`}
              >
                <TextArea
                  id={`${formId}-resolution`}
                  value={field.value}
                  onChange={(_event, value) => field.onChange(value)}
                  onBlur={field.onBlur}
                  validated={errors.resolution ? "error" : "default"}
                  resizeOrientation="vertical"
                />
                {errors.resolution ? (
                  <FormHelperText>
                    <HelperText>
                      <HelperTextItem variant="error">
                        {errors.resolution.message}
                      </HelperTextItem>
                    </HelperText>
                  </FormHelperText>
                ) : null}
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
          isLoading={closeRequest.isPending}
          isDisabled={closeRequest.isPending}
        >
          Cerrar solicitud
        </Button>
        <Button variant="link" onClick={onClose}>
          Cancelar
        </Button>
      </ModalFooter>
    </Modal>
  );
};
