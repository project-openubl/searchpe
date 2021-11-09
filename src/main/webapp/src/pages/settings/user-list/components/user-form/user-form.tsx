import React, { useState } from "react";
import { AxiosError, AxiosPromise, AxiosResponse } from "axios";
import { useFormik, FormikProvider, FormikHelpers } from "formik";
import { object, string, array } from "yup";

import {
  ActionGroup,
  Alert,
  Button,
  ButtonVariant,
  Form,
  FormGroup,
  TextInput,
} from "@patternfly/react-core";

import { createUser, updateUser } from "api/rest";
import { User } from "api/models";
import {
  getAxiosErrorMessage,
  getValidatedFromError,
  getValidatedFromErrorTouched,
} from "utils/modelUtils";
import { ALL_PERMISSIONS, Permission } from "Constants";
import { SimpleSelectMultipleFormikField } from "shared/components/simple-select";

export interface FormValues {
  fullName: string;
  username: string;
  password: string;
  permissions: Permission[];
}

export interface UserFormProps {
  user?: User;
  onSaved: (response: AxiosResponse<User>) => void;
  onCancel: () => void;
}

export const UserForm: React.FC<UserFormProps> = ({
  user,
  onSaved,
  onCancel,
}) => {
  const [error, setError] = useState<AxiosError>();

  const initialValues: FormValues = {
    fullName: user?.fullName || "",
    username: user?.username || "",
    password: "",
    permissions: user?.permissions || [],
  };

  const validationSchema = object().shape({
    fullName: string().trim().max(250),
    username: string().trim().required().min(3).max(120),
    password: string().trim().required().min(3).max(250),
    permissions: array().required().min(1),
  });

  const onSubmit = (
    formValues: FormValues,
    formikHelpers: FormikHelpers<FormValues>
  ) => {
    const payload: User = {
      fullName: formValues.fullName.trim(),
      username: formValues.username.trim(),
      password: formValues.password.trim(),
      permissions: formValues.permissions,
    };

    let promise: AxiosPromise<User>;
    if (user) {
      promise = updateUser({
        ...user,
        ...payload,
      });
    } else {
      promise = createUser(payload);
    }

    promise
      .then((response) => {
        formikHelpers.setSubmitting(false);
        onSaved(response);
      })
      .catch((error) => {
        formikHelpers.setSubmitting(false);
        setError(error);
      });
  };

  const formik = useFormik({
    enableReinitialize: true,
    initialValues: initialValues,
    validationSchema: validationSchema,
    onSubmit: onSubmit,
  });

  const onChangeField = (value: string, event: React.FormEvent<any>) => {
    formik.handleChange(event);
  };

  return (
    <FormikProvider value={formik}>
      <Form onSubmit={formik.handleSubmit}>
        {error && (
          <Alert
            variant="danger"
            isInline
            title={getAxiosErrorMessage(error)}
          />
        )}
        <FormGroup
          label="Nombre"
          fieldId="fullName"
          isRequired={true}
          validated={getValidatedFromError(formik.errors.fullName)}
          helperTextInvalid={formik.errors.fullName}
        >
          <TextInput
            type="text"
            name="fullName"
            aria-label="fullName"
            aria-describedby="fullName"
            isRequired={true}
            onChange={onChangeField}
            onBlur={formik.handleBlur}
            value={formik.values.fullName}
            validated={getValidatedFromErrorTouched(
              formik.errors.fullName,
              formik.touched.fullName
            )}
          />
        </FormGroup>
        <FormGroup
          label="Permisos"
          fieldId="permissions"
          isRequired={true}
          validated={getValidatedFromError(formik.errors.permissions)}
          helperTextInvalid={formik.errors.permissions}
        >
          <SimpleSelectMultipleFormikField
            fieldConfig={{ name: "permissions" }}
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
          validated={getValidatedFromError(formik.errors.username)}
          helperTextInvalid={formik.errors.username}
        >
          <TextInput
            type="text"
            name="username"
            aria-label="username"
            aria-describedby="username"
            isRequired={true}
            onChange={onChangeField}
            onBlur={formik.handleBlur}
            value={formik.values.username}
            validated={getValidatedFromErrorTouched(
              formik.errors.username,
              formik.touched.username
            )}
            autoComplete="off"
          />
        </FormGroup>
        <FormGroup
          label="Contraseña"
          fieldId="password"
          isRequired={true}
          validated={getValidatedFromError(formik.errors.password)}
          helperTextInvalid={formik.errors.password}
        >
          <TextInput
            type="password"
            name="password"
            aria-label="password"
            aria-describedby="password"
            isRequired={true}
            onChange={onChangeField}
            onBlur={formik.handleBlur}
            value={formik.values.password}
            validated={getValidatedFromErrorTouched(
              formik.errors.password,
              formik.touched.password
            )}
          />
        </FormGroup>

        <ActionGroup>
          <Button
            type="submit"
            aria-label="submit"
            variant={ButtonVariant.primary}
            isDisabled={
              !formik.isValid ||
              !formik.dirty ||
              formik.isSubmitting ||
              formik.isValidating
            }
          >
            {!user ? "Crear" : "Guardar"}
          </Button>
          <Button
            type="button"
            aria-label="cancel"
            variant={ButtonVariant.link}
            isDisabled={formik.isSubmitting || formik.isValidating}
            onClick={onCancel}
          >
            Cancelar
          </Button>
        </ActionGroup>
      </Form>
    </FormikProvider>
  );
};
