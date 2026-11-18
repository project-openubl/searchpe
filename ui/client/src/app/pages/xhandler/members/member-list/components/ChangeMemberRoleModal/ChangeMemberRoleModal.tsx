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
} from "@patternfly/react-core";

import type { MemberView } from "@app/client/xhandler";
import { FormSubmitError } from "@app/components/FormSubmitError";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSubmitError } from "@app/hooks/useSubmitError";
import { TENANT_ROLE_LABELS } from "@app/hooks/useTenantRole";
import {
  ASSIGNABLE_MEMBER_ROLES,
  type AssignableMemberRole,
  useMemberUpdateMutation,
} from "@app/queries/xhandler/members";
import { getMutationErrorMessage } from "@app/utils/apiError";

import { changeMemberRoleSchema } from "./changeMemberRoleSchema";

interface IChangeMemberRoleModalProps {
  /** The member whose role changes; null keeps the modal closed. */
  member: MemberView | null;
  onClose: () => void;
}

/** Changes a member's role (admin, operator or viewer). */
export const ChangeMemberRoleModal: React.FC<IChangeMemberRoleModalProps> = ({
  member,
  onClose,
}) => (
  <Modal
    isOpen={member !== null}
    onClose={onClose}
    variant="small"
    aria-labelledby="change-member-role-modal-title"
  >
    {member ? <ChangeMemberRoleForm member={member} onClose={onClose} /> : null}
  </Modal>
);

const isAssignable = (role: string | undefined): role is AssignableMemberRole =>
  (ASSIGNABLE_MEMBER_ROLES as readonly string[]).includes(role ?? "");

const ChangeMemberRoleForm: React.FC<{
  member: MemberView;
  onClose: () => void;
}> = ({ member, onClose }) => {
  const formId = useId();
  const { addNotification } = useNotifications();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();
  const { control, handleSubmit } = useForm({
    resolver: yupResolver(changeMemberRoleSchema),
    // An owner being demoted starts on admin, the role next to it
    defaultValues: { role: isAssignable(member.role) ? member.role : "admin" },
    mode: "onTouched",
  });

  const update = useMemberUpdateMutation({
    onSuccess: (data) =>
      addNotification({
        variant: "success",
        title: `${data.subject ?? "El miembro"} ahora es ${TENANT_ROLE_LABELS[data.role as AssignableMemberRole] ?? data.role}`,
      }),
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: "No se pudo cambiar el rol",
        }),
      ),
  });

  const onSubmit = handleSubmit(({ role }) => {
    clearSubmitError();
    update.mutate({ member, role }, { onSuccess: onClose });
  });

  return (
    <>
      <ModalHeader
        title="Cambiar rol"
        description={member.subject}
        labelId="change-member-role-modal-title"
      />
      <ModalBody>
        <Form id={formId} onSubmit={onSubmit}>
          <FormSubmitError error={submitError} />
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
          isLoading={update.isPending}
          isDisabled={update.isPending}
        >
          Guardar
        </Button>
        <Button variant="link" onClick={onClose}>
          Cancelar
        </Button>
      </ModalFooter>
    </>
  );
};
