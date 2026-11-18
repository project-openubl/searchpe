import type React from "react";
import { useId } from "react";
import { Controller, useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";
import { mixed, object, string } from "yup";

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

import { ControlledTextField } from "@app/components/ControlledTextField";
import { FormSubmitError } from "@app/components/FormSubmitError";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSubmitError } from "@app/hooks/useSubmitError";
import { useCertificateUploadMutation } from "@app/queries/xhandler/certificates";
import { getMutationErrorMessage } from "@app/utils/apiError";

const certificateSchema = object({
  file: mixed<File>()
    .required("Elige el archivo .pfx o .p12")
    .test(
      "pkcs12",
      "El certificado debe ser un archivo .pfx o .p12",
      (value) => !value || /\.(pfx|p12)$/i.test(value.name),
    ),
  password: string().required("Ingresa la contraseña del certificado"),
});

interface ICertificateUploadModalProps {
  isOpen: boolean;
  companyId: string;
  onClose: () => void;
}

/** Uploads a PKCS#12 keystore; its key goes to the vault and the password is never stored. */
export const CertificateUploadModal: React.FC<ICertificateUploadModalProps> = ({
  isOpen,
  companyId,
  onClose,
}) => (
  <Modal
    isOpen={isOpen}
    onClose={onClose}
    variant="small"
    aria-labelledby="certificate-modal-title"
  >
    {isOpen ? (
      <CertificateUploadForm companyId={companyId} onClose={onClose} />
    ) : null}
  </Modal>
);

interface ICertificateUploadFormProps {
  companyId: string;
  onClose: () => void;
}

const CertificateUploadForm: React.FC<ICertificateUploadFormProps> = ({
  companyId,
  onClose,
}) => {
  const formId = useId();
  const { addNotification } = useNotifications();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();
  const { control, handleSubmit } = useForm({
    resolver: yupResolver(certificateSchema),
    defaultValues: { password: "" },
  });

  const upload = useCertificateUploadMutation(companyId, {
    onSuccess: () =>
      addNotification({
        variant: "success",
        title: "Se cargó el certificado digital",
      }),
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: "No se pudo cargar el certificado",
        }),
      ),
  });

  const onSubmit = handleSubmit(({ file, password }) => {
    clearSubmitError();
    upload.mutate({ file, password }, { onSuccess: onClose });
  });

  return (
    <>
      <ModalHeader
        title="Subir certificado digital"
        labelId="certificate-modal-title"
        description="Reemplaza el certificado activo de la empresa."
      />
      <ModalBody>
        <Form id={formId} onSubmit={onSubmit}>
          <FormSubmitError error={submitError} />
          <Controller
            control={control}
            name="file"
            render={({ field, fieldState }) => (
              <FormGroup
                label="Archivo .pfx o .p12"
                isRequired
                fieldId={`${formId}-file`}
              >
                <FileUpload
                  id={`${formId}-file`}
                  filename={field.value?.name ?? ""}
                  filenamePlaceholder="Arrastra el archivo o elígelo"
                  browseButtonText="Elegir"
                  clearButtonText="Quitar"
                  hideDefaultPreview
                  accept=".pfx,.p12,application/x-pkcs12"
                  onFileInputChange={(_event, file) => field.onChange(file)}
                  onClearClick={() => field.onChange(undefined)}
                  validated={fieldState.error ? "error" : "default"}
                />
                {fieldState.error ? (
                  <FormHelperText>
                    <HelperText>
                      <HelperTextItem variant="error">
                        {fieldState.error.message}
                      </HelperTextItem>
                    </HelperText>
                  </FormHelperText>
                ) : null}
              </FormGroup>
            )}
          />
          <ControlledTextField
            control={control}
            name="password"
            label="Contraseña"
            type="password"
            autoComplete="off"
            isRequired
            helperText="Se usa una sola vez para importar la clave; no se guarda."
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
          Subir certificado
        </Button>
        <Button variant="link" onClick={onClose}>
          Cancelar
        </Button>
      </ModalFooter>
    </>
  );
};
