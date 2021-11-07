import React, { useState } from "react";
import { AxiosError, AxiosPromise, AxiosResponse } from "axios";
import { useFormik, FormikProvider, FormikHelpers } from "formik";
import { object, string } from "yup";

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

export interface FormValues {
  username: string;
  password: string;
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
    username: user?.username || "",
    password: "",
  };

  const validationSchema = object().shape({
    username: string().trim().required().min(3).max(120),
    password: string().trim().required().min(3).max(250),
  });

  const onSubmit = (
    formValues: FormValues,
    formikHelpers: FormikHelpers<FormValues>
  ) => {
    const payload: User = {
      username: formValues.username.trim(),
      password: formValues.password.trim(),
      role: "",
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
