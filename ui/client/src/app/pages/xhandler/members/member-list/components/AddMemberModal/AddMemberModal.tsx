import type React from "react";
import { useId } from "react";
import { Controller, useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";

import {
  Button,
  Form,
  FormGroup,
  FormSelect,
  FormSelectOption,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
  Radio,
} from "@patternfly/react-core";

import { ControlledTextField } from "@app/components/ControlledTextField";
import { FormSubmitError } from "@app/components/FormSubmitError";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSubmitError } from "@app/hooks/useSubmitError";
import { TENANT_ROLE_LABELS } from "@app/hooks/useTenantRole";
import {
  ASSIGNABLE_MEMBER_ROLES,
  MEMBER_KINDS,
  type MemberKind,
  useMemberAddMutation,
} from "@app/queries/xhandler/members";
import { getMutationErrorMessage } from "@app/utils/apiError";

import { ADD_MEMBER_DEFAULTS, addMemberSchema } from "./addMemberSchema";

const KIND_PRESENTATION: Record<
  MemberKind,
  { label: string; description: string; subjectLabel: string; helper: string }
> = {
  user: {
    label: "Persona",
    description: "Alguien que entra a la consola con su cuenta.",
    subjectLabel: "Identificador del usuario",
    helper: "El identificador (sub) de su cuenta en Keycloak.",
  },
  client: {
    label: "Cliente de API",
    description: "Un sistema (ERP) que llama a la API con client credentials.",
    subjectLabel: "Client ID",
    helper: "El client ID de su cuenta de servicio en Keycloak.",
  },
};

interface IAddMemberModalProps {
  isOpen: boolean;
  onClose: () => void;
}

/** Adds a person or an API client to the organization with a role. */
export const AddMemberModal: React.FC<IAddMemberModalProps> = ({
  isOpen,
  onClose,
}) => (
  <Modal
    isOpen={isOpen}
    onClose={onClose}
    variant="small"
    aria-labelledby="add-member-modal-title"
  >
    {isOpen ? <AddMemberForm onClose={onClose} /> : null}
  </Modal>
);

interface IAddMemberFormProps {
  onClose: () => void;
}

const AddMemberForm: React.FC<IAddMemberFormProps> = ({ onClose }) => {
  const formId = useId();
  const { addNotification } = useNotifications();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();
  const { control, handleSubmit, watch } = useForm({
    resolver: yupResolver(addMemberSchema),
    defaultValues: ADD_MEMBER_DEFAULTS,
    mode: "onTouched",
  });
  const kind = watch("kind");

  const add = useMemberAddMutation({
    onSuccess: (member) =>
      addNotification({
        variant: "success",
        title: `Se agregó ${member.subject ?? "el miembro"}`,
      }),
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: "No se pudo agregar el miembro",
        }),
      ),
  });

  const onSubmit = handleSubmit((values) => {
    clearSubmitError();
    add.mutate(values, { onSuccess: onClose });
  });

  return (
    <>
      <ModalHeader title="Agregar miembro" labelId="add-member-modal-title" />
      <ModalBody>
        <Form id={formId} onSubmit={onSubmit}>
          <FormSubmitError error={submitError} />
          <FormGroup role="radiogroup" label="Tipo" isStack fieldId={formId}>
            <Controller
              control={control}
              name="kind"
              render={({ field }) => (
                <>
                  {MEMBER_KINDS.map((value) => (
                    <Radio
                      key={value}
                      id={`${formId}-${value}`}
                      name={field.name}
                      label={KIND_PRESENTATION[value].label}
                      description={KIND_PRESENTATION[value].description}
                      isChecked={field.value === value}
                      onChange={() => field.onChange(value)}
                    />
                  ))}
                </>
              )}
            />
          </FormGroup>
          <ControlledTextField
            control={control}
            name="subject"
            label={KIND_PRESENTATION[kind].subjectLabel}
            helperText={KIND_PRESENTATION[kind].helper}
            isRequired
          />
          <Controller
            control={control}
            name="role"
            render={({ field }) => (
              <FormGroup label="Rol" isRequired fieldId={`${formId}-role`}>
                <FormSelect
                  id={`${formId}-role`}
                  value={field.value}
                  onChange={(_event, value) => field.onChange(value)}
                >
                  {ASSIGNABLE_MEMBER_ROLES.map((role) => (
                    <FormSelectOption
                      key={role}
                      value={role}
                      label={TENANT_ROLE_LABELS[role]}
                    />
                  ))}
                </FormSelect>
              </FormGroup>
            )}
          />
        </Form>
      </ModalBody>
      <ModalFooter>
        <Button
          type="submit"
          form={formId}
          isLoading={add.isPending}
          isDisabled={add.isPending}
        >
          Agregar
        </Button>
        <Button variant="link" onClick={onClose}>
          Cancelar
        </Button>
      </ModalFooter>
    </>
  );
};
