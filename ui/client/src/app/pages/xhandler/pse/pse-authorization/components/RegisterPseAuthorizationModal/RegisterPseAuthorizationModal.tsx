import type React from "react";
import { useId } from "react";
import { Controller, useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";
import dayjs from "dayjs";
import { type InferType, mixed, object, string } from "yup";

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
  TextInput,
} from "@patternfly/react-core";

import { FormSubmitError } from "@app/components/FormSubmitError";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSubmitError } from "@app/hooks/useSubmitError";
import { useAuthorizePseMutation } from "@app/queries/xhandler/pse";
import { getMutationErrorMessage } from "@app/utils/apiError";

import { EVIDENCE_MAX_BYTES, EVIDENCE_TYPES, fileToBase64 } from "./evidence";

interface IRegisterPseAuthorizationModalProps {
  companyId: string;
  isOpen: boolean;
  onClose: () => void;
}

const schema = object({
  authorized_at: string()
    .required("Ingresa la fecha de la autorización")
    .matches(/^\d{4}-\d{2}-\d{2}$/, "Ingresa una fecha válida")
    .test(
      "not-future",
      "La fecha de autorización no puede ser futura",
      (value) => !value || !dayjs(value).isAfter(dayjs(), "day"),
    ),
  evidence: mixed<File>()
    .required("Adjunta la constancia de SUNAT")
    .test(
      "type",
      "La constancia debe ser un PDF, PNG o JPEG",
      (file) => !file || EVIDENCE_TYPES.includes(file.type),
    )
    .test(
      "size",
      "La constancia no debe pasar de 2 MB",
      (file) => !file || file.size <= EVIDENCE_MAX_BYTES,
    ),
});

type IRegisterPseAuthorizationValues = InferType<typeof schema>;

/**
 * Registers the tenant's authorization of the platform as PSE in SUNAT's SOL, with its evidence
 * (a PDF or image of SOL's confirmation), which a platform admin approves (admins).
 */
export const RegisterPseAuthorizationModal: React.FC<
  IRegisterPseAuthorizationModalProps
> = ({ companyId, isOpen, onClose }) => (
  <Modal
    isOpen={isOpen}
    onClose={onClose}
    variant="medium"
    aria-labelledby="register-pse-authorization-title"
  >
    {/* Mounted only while open, so every open starts with an empty form. */}
    {isOpen ? (
      <RegisterPseAuthorizationForm companyId={companyId} onClose={onClose} />
    ) : null}
  </Modal>
);

interface IRegisterPseAuthorizationFormProps {
  companyId: string;
  onClose: () => void;
}

const RegisterPseAuthorizationForm: React.FC<
  IRegisterPseAuthorizationFormProps
> = ({ companyId, onClose }) => {
  const formId = useId();
  const { addNotification } = useNotifications();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();
  const authorize = useAuthorizePseMutation(companyId, {
    onSuccess: () =>
      addNotification({
        variant: "success",
        title: "Se registró la autorización de PSE",
        description: "La plataforma revisará la constancia antes de aprobarla.",
      }),
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: "No se pudo registrar la autorización",
        }),
      ),
  });

  const {
    control,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<IRegisterPseAuthorizationValues>({
    resolver: yupResolver(schema),
    defaultValues: { authorized_at: "" },
  });

  const onSubmit = async (values: IRegisterPseAuthorizationValues) => {
    clearSubmitError();
    const evidenceBase64 = await fileToBase64(values.evidence);
    authorize.mutate(
      { authorized_at: values.authorized_at, evidence_base64: evidenceBase64 },
      { onSuccess: onClose },
    );
  };

  const isBusy = isSubmitting || authorize.isPending;

  return (
    <>
      <ModalHeader
        title="Registrar autorización de PSE"
        labelId="register-pse-authorization-title"
        description="Primero autoriza a la plataforma como PSE en SUNAT Operaciones en Línea (SOL); luego registra aquí esa autorización con su constancia."
      />
      <ModalBody>
        <Form
          id={formId}
          onSubmit={(event) => void handleSubmit(onSubmit)(event)}
        >
          <FormSubmitError error={submitError} />
          <FormGroup
            label="Fecha de la autorización en SOL"
            isRequired
            fieldId={`${formId}-date`}
          >
            <Controller
              name="authorized_at"
              control={control}
              render={({ field }) => (
                <TextInput
                  id={`${formId}-date`}
                  type="date"
                  value={field.value}
                  max={dayjs().format("YYYY-MM-DD")}
                  onChange={(_event, value) => field.onChange(value)}
                  onBlur={field.onBlur}
                  validated={errors.authorized_at ? "error" : "default"}
                />
              )}
            />
            {errors.authorized_at ? (
              <FormHelperText>
                <HelperText>
                  <HelperTextItem variant="error">
                    {errors.authorized_at.message}
                  </HelperTextItem>
                </HelperText>
              </FormHelperText>
            ) : null}
          </FormGroup>
          <FormGroup
            label="Constancia de SUNAT"
            isRequired
            fieldId={`${formId}-evidence`}
          >
            <Controller
              name="evidence"
              control={control}
              render={({ field }) => (
                <FileUpload
                  id={`${formId}-evidence`}
                  type="dataURL"
                  hideDefaultPreview
                  filename={field.value?.name ?? ""}
                  filenamePlaceholder="Arrastra un archivo o selecciónalo"
                  browseButtonText="Seleccionar"
                  clearButtonText="Quitar"
                  onFileInputChange={(_event, file) => field.onChange(file)}
                  onClearClick={() => field.onChange(undefined)}
                  dropzoneProps={{
                    accept: {
                      "application/pdf": [".pdf"],
                      "image/png": [".png"],
                      "image/jpeg": [".jpg", ".jpeg"],
                    },
                  }}
                  validated={errors.evidence ? "error" : "default"}
                />
              )}
            />
            <FormHelperText>
              <HelperText>
                <HelperTextItem variant={errors.evidence ? "error" : "default"}>
                  {errors.evidence?.message ??
                    "Un PDF o una imagen (PNG o JPEG) de la confirmación de SOL, de hasta 2 MB."}
                </HelperTextItem>
              </HelperText>
            </FormHelperText>
          </FormGroup>
        </Form>
      </ModalBody>
      <ModalFooter>
        <Button
          type="submit"
          form={formId}
          isLoading={isBusy}
          isDisabled={isBusy}
        >
          Registrar
        </Button>
        <Button variant="link" onClick={onClose}>
          Cancelar
        </Button>
      </ModalFooter>
    </>
  );
};
