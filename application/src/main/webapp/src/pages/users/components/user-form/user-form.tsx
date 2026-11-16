import React, { useState } from "react";

import { useForm, Controller } from "react-hook-form";
import { yupResolver } from "@hookform/resolvers/yup";
import { object, string, array, ObjectSchema } from "yup";

import {
  ActionGroup,
  Alert,
  Button,
  ButtonVariant,
  Form,
  FormGroup,
  InputGroup,
  InputGroupItem,
  TextInput,
} from "@patternfly/react-core";
import { EditAltIcon } from "@patternfly/react-icons";

import { useCreateUserMutation, useUpdateUserMutation } from "queries/users";
import { ControllerSelectMultiple, FormFieldError } from "shared/components";

import { User } from "api/models";
import {
  getAxiosErrorMessage,
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

  const [error, setError] = useState<any>();
  const [isEditingPassword, setIsEditingPassword] = useState(false);

  const initialValues: FormValues = {
    fullName: user?.fullName || "",
    username: user?.username || "",
    password: "",
    permissions: user?.permissions || [],
  };

  const validationSchema: ObjectSchema<FormValues> = object({
    fullName: string().trim().max(250).defined(),
    username: string()
      .trim()
      .required()
      .min(3)
      .max(120)
      .matches(/^[a-zA-Z0-9._-]{3,}$/),
    password:
      user && !isEditingPassword
        ? string().max(250).defined()
        : string().required().min(3).max(250),
    permissions: array().of(string<Permission>().defined()).required().min(1),
  });

  const {
    handleSubmit,
    formState: { errors, isSubmitting, isValidating, isValid, isDirty },
    control,
    reset,
  } = useForm<FormValues>({
    defaultValues: initialValues,
    resolver: yupResolver(validationSchema),
    mode: "onChange",
  });

  const onSubmit = (formValues: FormValues) => {
    const payload: User = {
      fullName: formValues.fullName.trim(),
      username: formValues.username,
      password: formValues.password !== "" ? formValues.password : undefined,
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

  return (
    <Form onSubmit={handleSubmit(onSubmit)}>
      {error && (
        <Alert variant="danger" isInline title={getAxiosErrorMessage(error)} />
      )}
      <FormGroup label="Nombre" fieldId="fullName" isRequired={false}>
        <Controller
          control={control}
          name="fullName"
          render={({
            field: { onChange, onBlur, value, name },
            fieldState: { isTouched, error },
          }) => (
            <TextInput
              type="text"
              id="fullName"
              name={name}
              aria-label="fullName"
              aria-describedby="fullName"
              isRequired={false}
              onChange={(_event, value) => onChange(value)}
              onBlur={onBlur}
              value={value}
              validated={getValidatedFromErrorTouched(error, isTouched)}
            />
          )}
        />
        <FormFieldError message={errors.fullName?.message} />
      </FormGroup>
      <FormGroup label="Permisos" fieldId="permissions" isRequired={true}>
        <ControllerSelectMultiple
          fieldConfig={{ name: "permissions", control: control }}
          selectConfig={{
            "aria-label": "permissions",
            "aria-describedby": "permissions",
            placeholderText: "Permisos asignados",
          }}
          options={ALL_PERMISSIONS}
          isEqual={(a, b) => a === b}
        />
        <FormFieldError message={errors.permissions?.message} />
      </FormGroup>
      <FormGroup label="Usuario" fieldId="username" isRequired={true}>
        <Controller
          control={control}
          name="username"
          render={({
            field: { onChange, onBlur, value, name },
            fieldState: { isTouched, error },
          }) => (
            <TextInput
              type="text"
              id="username"
              name={name}
              aria-label="username"
              aria-describedby="username"
              isRequired={true}
              onChange={(_event, value) => onChange(value)}
              onBlur={onBlur}
              value={value}
              validated={getValidatedFromErrorTouched(error, isTouched)}
              autoComplete="off"
            />
          )}
        />
        <FormFieldError message={errors.username?.message} />
      </FormGroup>
      {user ? (
        <FormGroup
          label="Contraseña"
          fieldId="password"
          isRequired={isEditingPassword}
        >
          <Controller
            control={control}
            name="password"
            render={({
              field: { onChange, onBlur, value, name },
              fieldState: { isTouched, error },
            }) => (
              <InputGroup>
                <InputGroupItem isFill>
                  <TextInput
                    type="password"
                    id="password"
                    name={name}
                    aria-label="password"
                    aria-describedby="password"
                    isRequired={isEditingPassword}
                    onChange={(_event, value) => onChange(value)}
                    onBlur={onBlur}
                    value={isEditingPassword ? value : "******"}
                    validated={
                      isEditingPassword
                        ? getValidatedFromErrorTouched(error, isTouched)
                        : "default"
                    }
                    isDisabled={!isEditingPassword}
                  />
                </InputGroupItem>
                <InputGroupItem>
                  <Button
                    variant="control"
                    aria-label="change-password"
                    icon={<EditAltIcon />}
                    onClick={() => {
                      onChange("");
                      setIsEditingPassword((current) => !current);
                    }}
                  />
                </InputGroupItem>
              </InputGroup>
            )}
          />
          {isEditingPassword && (
            <FormFieldError message={errors.password?.message} />
          )}
        </FormGroup>
      ) : (
        <FormGroup label="Contraseña" fieldId="password" isRequired={true}>
          <Controller
            control={control}
            name="password"
            render={({
              field: { onChange, onBlur, value, name },
              fieldState: { isTouched, error },
            }) => (
              <TextInput
                type="password"
                id="password"
                name={name}
                aria-label="password"
                aria-describedby="password"
                isRequired={true}
                onChange={(_event, value) => onChange(value)}
                onBlur={onBlur}
                value={value}
                validated={getValidatedFromErrorTouched(error, isTouched)}
              />
            )}
          />
          <FormFieldError message={errors.password?.message} />
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
