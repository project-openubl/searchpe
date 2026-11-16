import React, { useState } from "react";

import {
  ActionGroup,
  Alert,
  Button,
  ButtonVariant,
  Card,
  CardBody,
  Form,
  FormGroup,
  TextInput,
} from "@patternfly/react-core";

import { useForm, Controller } from "react-hook-form";
import { yupResolver } from "@hookform/resolvers/yup";
import { object, string, ref, ObjectSchema } from "yup";

import { AxiosError } from "axios";
import { useUpdateCurrentUserPasswordMutation } from "queries/currentUser";
import { FormFieldError } from "shared/components";
import { useNotificationContext } from "shared/context";

import {
  getAxiosErrorMessage,
  getValidatedFromErrorTouched,
} from "utils/modelUtils";

interface FormValues {
  newPassword: string;
  confirmNewPassword: string;
}

const validationSchema: ObjectSchema<FormValues> = object({
  newPassword: string().trim().max(250).defined(),
  confirmNewPassword: string()
    .trim()
    .max(250)
    .oneOf([ref("newPassword")], "Passwords must match")
    .defined(),
});

export const PasswordAndAuthentication: React.FC = () => {
  const { pushNotification } = useNotificationContext();

  // Form

  const {
    handleSubmit,
    formState: { errors, isSubmitting, isValidating, isValid, isDirty },
    control,
    reset,
  } = useForm<FormValues>({
    defaultValues: {
      newPassword: "",
      confirmNewPassword: "",
    },
    resolver: yupResolver(validationSchema),
    mode: "onChange",
  });

  const onSubmit = (formValues: FormValues) => {
    const newPassword = formValues.newPassword;
    return updatePasswordMutation.mutateAsync({ newPassword });
  };

  // Query
  const [updateError, setUpdateError] = useState<AxiosError>();

  const onUpdatePasswordSuccess = () => {
    pushNotification({
      variant: "success",
      title: "Éxito",
      message: "Password actualizado",
    });
    reset();
  };

  const onUpdatePasswordError = (error: AxiosError) => {
    setUpdateError(error);
  };

  const updatePasswordMutation = useUpdateCurrentUserPasswordMutation(
    onUpdatePasswordSuccess,
    onUpdatePasswordError
  );

  return (
    <Card>
      <CardBody>
        <Form onSubmit={handleSubmit(onSubmit)}>
          {updateError && (
            <Alert
              variant="danger"
              isInline
              title={getAxiosErrorMessage(updateError)}
            />
          )}
          <FormGroup
            label="New password"
            fieldId="newPassword"
            isRequired={true}
          >
            <Controller
              control={control}
              name="newPassword"
              render={({
                field: { onChange, value, name },
                fieldState: { isTouched, error },
              }) => (
                <TextInput
                  onChange={(_event, value) => onChange(value)}
                  type="password"
                  id="newPassword"
                  name={name}
                  aria-label="new-password"
                  aria-describedby="new-password"
                  isRequired={true}
                  value={value}
                  validated={getValidatedFromErrorTouched(error, isTouched)}
                />
              )}
            />
            <FormFieldError message={errors.newPassword?.message} />
          </FormGroup>
          <FormGroup
            label="Confirm new password"
            fieldId="confirmNewPassword"
            isRequired={true}
          >
            <Controller
              control={control}
              name="confirmNewPassword"
              render={({
                field: { onChange, value, name },
                fieldState: { isTouched, error },
              }) => (
                <TextInput
                  onChange={(_event, value) => onChange(value)}
                  type="password"
                  id="confirmNewPassword"
                  name={name}
                  aria-label="confirm-new-password"
                  aria-describedby="confirm-new-password"
                  isRequired={true}
                  value={value}
                  validated={getValidatedFromErrorTouched(error, isTouched)}
                />
              )}
            />
            <FormFieldError message={errors.confirmNewPassword?.message} />
          </FormGroup>

          <ActionGroup>
            <Button
              type="submit"
              aria-label="submit"
              variant={ButtonVariant.primary}
              isDisabled={!isValid || isSubmitting || isValidating || !isDirty}
            >
              Guardar
            </Button>
          </ActionGroup>
        </Form>
      </CardBody>
    </Card>
  );
};
