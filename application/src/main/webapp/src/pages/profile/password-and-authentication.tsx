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

import { useForm, Controller, FieldValues } from "react-hook-form";
import { yupResolver } from "@hookform/resolvers/yup";
import { object, string, ref } from "yup";

import { useDispatch } from "react-redux";
import { alertActions } from "store/alert";

import { AxiosError } from "axios";
import { useUpdateCurrentUserPasswordMutation } from "queries/currentUser";

import {
  getAxiosErrorMessage,
  getValidatedFromError,
  getValidatedFromErrorTouched,
} from "utils/modelUtils";

export const PasswordAndAuthentication: React.FC = () => {
  const dispatch = useDispatch();

  // Form

  const validationSchema = object().shape({
    newPassword: string().trim().max(250),
    confirmNewPassword: string()
      .trim()
      .max(250)
      .oneOf([ref("newPassword"), null], "Passwords must match"),
  });

  const {
    handleSubmit,
    formState: { errors, isSubmitting, isValidating, isValid, isDirty },
    control,
    reset,
  } = useForm({
    defaultValues: {
      oldPassword: "",
      newPassword: "",
      confirmNewPassword: "",
    },
    resolver: yupResolver(validationSchema),
    mode: "onChange",
  });

  const onSubmit = (formValues: FieldValues) => {
    const newPassword = formValues.newPassword;
    return updatePasswordMutation.mutateAsync({ newPassword });
  };

  // Query
  const [updateError, setUpdateError] = useState<AxiosError>();

  const onUpdatePasswordSuccess = () => {
    dispatch(alertActions.addAlert("success", "Éxito", "Password actualizado"));
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
            validated={getValidatedFromError(errors.newPassword)}
            helperTextInvalid={errors?.newPassword?.message}
          >
            <Controller
              control={control}
              name="newPassword"
              render={({
                field: { onChange, value, name },
                fieldState: { isTouched, error },
              }) => (
                <TextInput
                  onChange={onChange}
                  type="password"
                  name={name}
                  aria-label="new-password"
                  aria-describedby="new-password"
                  isRequired={true}
                  value={value}
                  validated={getValidatedFromErrorTouched(error, isTouched)}
                />
              )}
            />
          </FormGroup>
          <FormGroup
            label="Confirm new password"
            fieldId="confirmNewPassword"
            isRequired={true}
            validated={getValidatedFromError(errors.confirmNewPassword)}
            helperTextInvalid={errors?.confirmNewPassword?.message}
          >
            <Controller
              control={control}
              name="confirmNewPassword"
              render={({
                field: { onChange, value, name },
                fieldState: { isTouched, error },
              }) => (
                <TextInput
                  onChange={onChange}
                  type="password"
                  name={name}
                  aria-label="confirm-new-password"
                  aria-describedby="confirm-new-password"
                  isRequired={true}
                  value={value}
                  validated={getValidatedFromErrorTouched(error, isTouched)}
                />
              )}
            />
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
