import type React from "react";
import { useId } from "react";
import { Controller, useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";
import { mixed, object } from "yup";

import {
  Button,
  FileUpload,
  Form,
  FormGroup,
  FormHelperText,
  HelperText,
  HelperTextItem,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
} from "@patternfly/react-core";

import { FormSubmitError } from "@app/components/FormSubmitError";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSubmitError } from "@app/hooks/useSubmitError";
import { usePartnerLogoMutation } from "@app/queries/xhandler/partners";
import { getMutationErrorMessage } from "@app/utils/apiError";
import { logoFileProblem } from "@app/utils/logoFile";

const schema = object({
  file: mixed<File>()
    .required("Elige una imagen")
    .test("logo", (value, context) => {
      const problem = logoFileProblem(value);
      return problem ? context.createError({ message: problem }) : true;
    }),
});

interface IPartnerLogoModalProps {
  isOpen: boolean;
  partnerId: string;
  onClose: () => void;
}

/** Uploads the partner's logo, printed on the PDFs of its companies that have none of their own. */
export const PartnerLogoModal: React.FC<IPartnerLogoModalProps> = ({
  isOpen,
  partnerId,
  onClose,
}) => (
  <Modal
    isOpen={isOpen}
    onClose={onClose}
    variant="small"
    aria-labelledby="partner-logo-modal-title"
  >
    {isOpen ? (
      <PartnerLogoForm partnerId={partnerId} onClose={onClose} />
    ) : null}
  </Modal>
);

interface IPartnerLogoFormProps {
  partnerId: string;
  onClose: () => void;
}

const PartnerLogoForm: React.FC<IPartnerLogoFormProps> = ({
  partnerId,
  onClose,
}) => {
  const formId = useId();
  const { addNotification } = useNotifications();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();
  const { control, handleSubmit } = useForm({
    resolver: yupResolver(schema),
  });

  const upload = usePartnerLogoMutation(partnerId, {
    onSuccess: () =>
      addNotification({ variant: "success", title: "Se actualizó el logo" }),
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: "No se pudo subir el logo",
        }),
      ),
  });

  const onSubmit = handleSubmit(({ file }) => {
    clearSubmitError();
    upload.mutate(file, { onSuccess: onClose });
  });

  return (
    <>
      <ModalHeader title="Subir logo" labelId="partner-logo-modal-title" />
      <ModalBody>
        <Form id={formId} onSubmit={onSubmit}>
          <FormSubmitError error={submitError} />
          <Controller
            control={control}
            name="file"
            render={({ field, fieldState }) => (
              <FormGroup label="Imagen" isRequired fieldId={`${formId}-file`}>
                <FileUpload
                  id={`${formId}-file`}
                  filename={field.value?.name ?? ""}
                  filenamePlaceholder="Arrastra una imagen o elígela"
                  browseButtonText="Elegir"
                  clearButtonText="Quitar"
                  hideDefaultPreview
                  accept="image/png,image/jpeg"
                  onFileInputChange={(_event, file) => field.onChange(file)}
                  onClearClick={() => field.onChange(undefined)}
                  validated={fieldState.error ? "error" : "default"}
                />
                <FormHelperText>
                  <HelperText>
                    <HelperTextItem
                      variant={fieldState.error ? "error" : "default"}
                    >
                      {fieldState.error?.message ??
                        "PNG o JPEG, hasta 200 KB y 2000 × 2000 píxeles."}
                    </HelperTextItem>
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
          isLoading={upload.isPending}
          isDisabled={upload.isPending}
        >
          Subir logo
        </Button>
        <Button variant="link" onClick={onClose}>
          Cancelar
        </Button>
      </ModalFooter>
    </>
  );
};
