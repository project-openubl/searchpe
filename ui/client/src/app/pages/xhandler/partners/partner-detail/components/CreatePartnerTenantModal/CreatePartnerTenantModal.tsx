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
  FormSelect,
  FormSelectOption,
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
import { useCreatePartnerTenantMutation } from "@app/queries/xhandler/partners";
import { getMutationErrorMessage } from "@app/utils/apiError";

interface ICreatePartnerTenantModalProps {
  partnerId: string;
  isOpen: boolean;
  onClose: () => void;
}

const schema = object({
  name: string()
    .trim()
    .required("Ingresa el nombre de la organización")
    .max(200, "El nombre tiene hasta 200 caracteres"),
  owner_kind: string().oneOf(["user", "client"]).required(),
  owner_subject: string()
    .trim()
    .max(200, "El identificador tiene hasta 200 caracteres")
    .default(""),
});

type ICreatePartnerTenantValues = InferType<typeof schema>;

const DEFAULT_VALUES: ICreatePartnerTenantValues = {
  name: "",
  owner_kind: "user",
  owner_subject: "",
};

/** Creates a tenant for one of the partner's clients (partner admins). */
export const CreatePartnerTenantModal: React.FC<
  ICreatePartnerTenantModalProps
> = ({ partnerId, isOpen, onClose }) => (
  <Modal
    isOpen={isOpen}
    onClose={onClose}
    variant="small"
    aria-labelledby="create-partner-tenant-title"
  >
    {/* Mounted only while open, so every open starts with an empty form. */}
    {isOpen ? (
      <CreatePartnerTenantForm partnerId={partnerId} onClose={onClose} />
    ) : null}
  </Modal>
);

interface ICreatePartnerTenantFormProps {
  partnerId: string;
  onClose: () => void;
}

const CreatePartnerTenantForm: React.FC<ICreatePartnerTenantFormProps> = ({
  partnerId,
  onClose,
}) => {
  const formId = useId();
  const { addNotification } = useNotifications();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();
  const createTenant = useCreatePartnerTenantMutation(partnerId, {
    onSuccess: (tenant) =>
      addNotification({
        variant: "success",
        title: `Se creó la organización ${tenant.name ?? ""}`.trim(),
      }),
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: "No se pudo crear la organización",
        }),
      ),
  });

  const {
    control,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<ICreatePartnerTenantValues>({
    resolver: yupResolver(schema),
    defaultValues: DEFAULT_VALUES,
  });
  const ownerSubject = watch("owner_subject");

  const onSubmit = (values: ICreatePartnerTenantValues) => {
    clearSubmitError();
    const subject = values.owner_subject.trim();
    createTenant.mutate(
      {
        name: values.name.trim(),
        ...(subject
          ? { owner_kind: values.owner_kind, owner_subject: subject }
          : {}),
      },
      { onSuccess: onClose },
    );
  };

  return (
    <>
      <ModalHeader
        title="Nueva organización"
        labelId="create-partner-tenant-title"
        description="Crea la organización de un cliente. Como administrador del socio, también la administras."
      />
      <ModalBody>
        <Form
          id={formId}
          onSubmit={(event) => void handleSubmit(onSubmit)(event)}
        >
          <FormSubmitError error={submitError} />
          <FormGroup
            label="Nombre de la organización"
            isRequired
            fieldId={`${formId}-name`}
          >
            <Controller
              name="name"
              control={control}
              render={({ field }) => (
                <TextInput
                  id={`${formId}-name`}
                  value={field.value}
                  onChange={(_event, value) => field.onChange(value)}
                  onBlur={field.onBlur}
                  validated={errors.name ? "error" : "default"}
                />
              )}
            />
            {errors.name ? (
              <FormHelperText>
                <HelperText>
                  <HelperTextItem variant="error">
                    {errors.name.message}
                  </HelperTextItem>
                </HelperText>
              </FormHelperText>
            ) : null}
          </FormGroup>
          <FormGroup
            label="Propietario (opcional)"
            fieldId={`${formId}-owner-subject`}
          >
            <Controller
              name="owner_subject"
              control={control}
              render={({ field }) => (
                <TextInput
                  id={`${formId}-owner-subject`}
                  value={field.value}
                  onChange={(_event, value) => field.onChange(value)}
                  onBlur={field.onBlur}
                  validated={errors.owner_subject ? "error" : "default"}
                />
              )}
            />
            <FormHelperText>
              <HelperText>
                <HelperTextItem
                  variant={errors.owner_subject ? "error" : "default"}
                >
                  {errors.owner_subject?.message ??
                    "El identificador (sub) del usuario o cliente de API que será propietario. Sin él, la organización queda solo a cargo del socio."}
                </HelperTextItem>
              </HelperText>
            </FormHelperText>
          </FormGroup>
          <FormGroup
            label="Tipo de propietario"
            fieldId={`${formId}-owner-kind`}
          >
            <Controller
              name="owner_kind"
              control={control}
              render={({ field }) => (
                <FormSelect
                  id={`${formId}-owner-kind`}
                  value={field.value}
                  isDisabled={!ownerSubject.trim()}
                  onChange={(_event, value) => field.onChange(value)}
                >
                  <FormSelectOption value="user" label="Usuario" />
                  <FormSelectOption value="client" label="Cliente de API" />
                </FormSelect>
              )}
            />
          </FormGroup>
        </Form>
      </ModalBody>
      <ModalFooter>
        <Button
          type="submit"
          form={formId}
          isLoading={createTenant.isPending}
          isDisabled={createTenant.isPending}
        >
          Crear
        </Button>
        <Button variant="link" onClick={onClose}>
          Cancelar
        </Button>
      </ModalFooter>
    </>
  );
};
