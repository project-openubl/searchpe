import React, { useState } from "react";

import { useForm, Controller, FieldValues, FieldErrors } from "react-hook-form";
import { yupResolver } from "@hookform/resolvers/yup";
import { object, string, array } from "yup";

import {
  ActionGroup,
  Alert,
  Button,
  ButtonVariant,
  Form,
  FormGroup,
  InputGroup,
  TextInput,
} from "@patternfly/react-core";
import { EditAltIcon } from "@patternfly/react-icons";

import { useCreateUserMutation, useUpdateUserMutation } from "queries/users";
import { ControllerSelectMultiple } from "shared/components";

import { User } from "api/models";
import {
  getAxiosErrorMessage,
  getValidatedFromError,
  getValidatedFromErrorTouched,
} from "utils/modelUtils";
import { ALL_PERMISSIONS, Permission } from "Constants";

export interface FormValues {
  fullName: string;
  username: string;
  password: string;
  permissions: Permission[];
}

export interface UserFormProps {
  user?: User;
  onSaved: (user: User) => void;
  onCancel: () => void;
}

export const UserForm: React.FC<UserFormProps> = ({
  user,
  onSaved,
  onCancel,
}) => {
  const createUserMutation = useCreateUserMutation();
  const updateUserMutation = useUpdateUserMutation();

  const [error, setError] = useState();
  const [isEditingPassword, setIsEditingPassword] = useState(false);

  const initialValues: FormValues = {
    fullName: user?.fullName || "",
    username: user?.username || "",
    password: "",
    permissions: user?.permissions || [],
  };

  const validationSchema = object().shape({
    fullName: string().trim().max(250),
    username: string()
      .trim()
      .required()
      .min(3)
      .max(120)
      .matches(/^[a-zA-Z0-9._-]{3,}$/),
    password:
      user && !isEditingPassword
        ? string().max(250)
        : string().required().min(3).max(250),
    permissions: array().required().min(1),
  });

  const onSubmit = (formValues: FieldValues) => {
    const payload: User = {
      fullName: formValues.fullName.trim(),
      username: formValues.username,
      password: (formValues.password !== ""
        ? formValues.password
        : undefined) as any,
      permissions: formValues.permissions,
    };
    let promise: Promise<User>;
    if (user) {
      promise = updateUserMutation.mutateAsync({ ...user, ...payload });
    } else {
      promise = createUserMutation.mutateAsync(payload);
    }
    return promise
      .then((response) => {
        reset();
        onSaved(response);
      })
      .catch((error) => {
        setError(error);
      });
  };

  const {
    handleSubmit,
    formState: { errors, isSubmitting, isValidating, isValid, isDirty },
    control,
    reset,
  } = useForm({
    defaultValues: initialValues,
    resolver: yupResolver(validationSchema),
    mode: "onChange",
  });

  return (
    <Form onSubmit={handleSubmit(onSubmit)}>
      {error && (
        <Alert variant="danger" isInline title={getAxiosErrorMessage(error)} />
      )}
      <FormGroup
        label="Nombre"
        fieldId="fullName"
        isRequired={false}
        validated={getValidatedFromError(errors.fullName)}
        helperTextInvalid={errors.fullName?.message}
      >
        <Controller
          control={control}
          name="fullName"
          render={({
            field: { onChange, onBlur, value, name },
            fieldState: { isTouched, error },
          }) => (
            <TextInput
              type="text"
              name={name}
              aria-label="fullName"
              aria-describedby="fullName"
              isRequired={false}
              onChange={onChange}
              onBlur={onBlur}
              value={value}
              validated={getValidatedFromErrorTouched(error, isTouched)}
            />
          )}
        />
      </FormGroup>
      <FormGroup
        label="Permisos"
        fieldId="permissions"
        isRequired={true}
        validated={getValidatedFromError(errors.permissions)}
        helperTextInvalid={(errors.permissions as FieldErrors)?.message}
      >
        <ControllerSelectMultiple
          fieldConfig={{ name: "permissions", control: control }}
          selectConfig={{
            variant: "checkbox",
            "aria-label": "permissions",
            "aria-describedby": "permissions",
            placeholderText: "Permisos asignados",
          }}
          options={ALL_PERMISSIONS}
          isEqual={(a, b) => a === b}
        />
      </FormGroup>
      <FormGroup
        label="Usuario"
        fieldId="username"
        isRequired={true}
        validated={getValidatedFromError(errors.username)}
        helperTextInvalid={errors.username?.message}
      >
        <Controller
          control={control}
          name="username"
          render={({
            field: { onChange, onBlur, value, name },
            fieldState: { isTouched, error },
          }) => (
            <TextInput
              type="text"
              name={name}
              aria-label="username"
              aria-describedby="username"
              isRequired={true}
              onChange={onChange}
              onBlur={onBlur}
              value={value}
              validated={getValidatedFromErrorTouched(error, isTouched)}
              autoComplete="off"
            />
          )}
        />
      </FormGroup>
      {user ? (
        <FormGroup
          label="Contraseña"
          fieldId="password"
          isRequired={isEditingPassword}
          validated={
            isEditingPassword
              ? getValidatedFromError(errors.password)
              : "default"
          }
          helperTextInvalid={isEditingPassword ? errors.password?.message : ""}
        >
          <Controller
            control={control}
            name="password"
            render={({
              field: { onChange, onBlur, value, name },
              fieldState: { isTouched, error },
            }) => (
              <InputGroup>
                <TextInput
                  type="password"
                  name={name}
                  aria-label="password"
                  aria-describedby="password"
                  isRequired={isEditingPassword}
                  onChange={onChange}
                  onBlur={onBlur}
                  value={isEditingPassword ? value : "******"}
                  validated={
                    isEditingPassword
                      ? getValidatedFromErrorTouched(error, isTouched)
                      : "default"
                  }
                  isDisabled={!isEditingPassword}
                />
                <Button
                  variant="control"
                  aria-label="change-password"
                  onClick={() => {
                    setIsEditingPassword((current) => {
                      onChange("");
                      return !current;
                    });
                  }}
                >
                  <EditAltIcon />
                </Button>
              </InputGroup>
            )}
          />
        </FormGroup>
      ) : (
        <FormGroup
          label="Contraseña"
          fieldId="password"
          isRequired={true}
          validated={getValidatedFromError(errors.password)}
          helperTextInvalid={errors.password?.message}
        >
          <Controller
            control={control}
            name="password"
            render={({
              field: { onChange, onBlur, value, name },
              fieldState: { isTouched, error },
            }) => (
              <TextInput
                type="password"
                name={name}
                aria-label="password"
                aria-describedby="password"
                isRequired={true}
                onChange={onChange}
                onBlur={onBlur}
                value={value}
                validated={getValidatedFromErrorTouched(error, isTouched)}
              />
            )}
          />
        </FormGroup>
      )}

      <ActionGroup>
        <Button
          type="submit"
          aria-label="submit"
          variant={ButtonVariant.primary}
          isDisabled={!isValid || !isDirty || isSubmitting || isValidating}
        >
          {!user ? "Crear" : "Guardar"}
        </Button>
        <Button
          type="button"
          aria-label="cancel"
          variant={ButtonVariant.link}
          isDisabled={isSubmitting || isValidating}
          onClick={onCancel}
        >
          Cancelar
        </Button>
      </ActionGroup>
    </Form>
  );
};
