import React, { useState } from "react";

import {
  ActionGroup,
  Alert,
  Bullseye,
  Button,
  ButtonVariant,
  Card,
  CardBody,
  EmptyState,
  EmptyStateVariant,
  Form,
  FormGroup,
  Grid,
  GridItem,
  Label,
  LabelGroup,
  TextInput,
} from "@patternfly/react-core";
import { UserCircleIcon } from "@patternfly/react-icons";

import { useForm, Controller } from "react-hook-form";
import { yupResolver } from "@hookform/resolvers/yup";
import { object, string, ObjectSchema } from "yup";

import { AxiosError } from "axios";

import { useWhoAmIQuery } from "queries/whoami";
import { useUpdateCurrentUserProfileMutation } from "queries/currentUser";
import { FormFieldError } from "shared/components";
import { useConfirmationContext } from "shared/context";

import {
  getAxiosErrorMessage,
  getValidatedFromErrorTouched,
} from "utils/modelUtils";
import { User } from "api/models";

interface FormValues {
  username: string;
  fullName: string;
}

const validationSchema: ObjectSchema<FormValues> = object({
  username: string().trim().required().min(3).max(250),
  fullName: string().trim().max(250).defined(),
});

export const Overview: React.FC = () => {
  const confirmation = useConfirmationContext();

  const whoAmI = useWhoAmIQuery();

  // Form

  const {
    handleSubmit,
    getFieldState,
    formState: { errors, isSubmitting, isValidating, isValid, isDirty },
    control,
  } = useForm<FormValues>({
    defaultValues: {
      username: whoAmI.data?.username || "",
      fullName: whoAmI.data?.fullName || "",
    },
    resolver: yupResolver(validationSchema),
    mode: "onChange",
  });

  const onSubmit = (formValues: FormValues) => {
    const user: User = {
      ...whoAmI.data,
      username: formValues.username,
      fullName: formValues.fullName,
    };

    const usernameField = getFieldState("username");
    if (usernameField.isDirty) {
      confirmation.open({
        title: "¿Cambiar nombre de usuario?",
        titleIconVariant: "warning",
        message:
          "Cambiar tu nombre de usuario puede causar que sesiones abiertas terminen y estas forzado a iniciar sesion nuevamente.",
        confirmBtnLabel: "Continuar",
        cancelBtnLabel: "Cancelar",
        confirmBtnVariant: ButtonVariant.primary,
        onConfirm: () => {
          confirmation.enableProcessing();
          updateProfileMutation.mutate(user);
        },
      });
    } else {
      return updateProfileMutation.mutateAsync(user);
    }
  };

  // Query
  const [updateError, setUpdateError] = useState<AxiosError>();

  const onUpdateProfileSuccess = () => {
    window.location.reload();
  };

  const onUpdateProfileError = (error: AxiosError) => {
    setUpdateError(error);
  };

  const updateProfileMutation = useUpdateCurrentUserProfileMutation(
    onUpdateProfileSuccess,
    onUpdateProfileError
  );

  return (
    <Card>
      <CardBody>
        <Grid hasGutter>
          <GridItem md={2}>
            <Card isCompact>
              <Bullseye>
                <EmptyState
                  headingLevel="h2"
                  icon={UserCircleIcon}
                  titleText="Profile picture"
                  variant={EmptyStateVariant.xs}
                />
              </Bullseye>
            </Card>
          </GridItem>
          <GridItem md={6}>
            <Form onSubmit={handleSubmit(onSubmit)}>
              {updateError && (
                <Alert
                  variant="danger"
                  isInline
                  title={getAxiosErrorMessage(updateError)}
                />
              )}
              <FormGroup label="Username" fieldId="username" isRequired={true}>
                <Controller
                  control={control}
                  name="username"
                  render={({
                    field: { onChange, value, name },
                    fieldState: { isTouched, error },
                  }) => (
                    <TextInput
                      onChange={(_event, value) => onChange(value)}
                      type="text"
                      id="username"
                      name={name}
                      aria-label="username"
                      aria-describedby="username"
                      isRequired={true}
                      value={value}
                      validated={getValidatedFromErrorTouched(error, isTouched)}
                    />
                  )}
                />
                <FormFieldError message={errors.username?.message} />
              </FormGroup>
              <FormGroup label="Full name" fieldId="fullName" isRequired={true}>
                <Controller
                  control={control}
                  name="fullName"
                  render={({
                    field: { onChange, value, name },
                    fieldState: { isTouched, error },
                  }) => (
                    <TextInput
                      onChange={(_event, value) => onChange(value)}
                      type="text"
                      id="fullName"
                      name={name}
                      aria-label="full-name"
                      aria-describedby="full-name"
                      isRequired={true}
                      value={value}
                      validated={getValidatedFromErrorTouched(error, isTouched)}
                    />
                  )}
                />
                <FormFieldError message={errors.fullName?.message} />
              </FormGroup>
              <FormGroup
                label="Permissions"
                fieldId="permissions"
                isRequired={false}
              >
                {/* Read-only; replaces a disabled PF4 typeahead-multi Select */}
                <LabelGroup aria-label="permissions" numLabels={10}>
                  {(whoAmI.data?.permissions || []).map((permission) => (
                    <Label key={permission}>{permission}</Label>
                  ))}
                </LabelGroup>
              </FormGroup>

              <ActionGroup>
                <Button
                  type="submit"
                  aria-label="submit"
                  variant={ButtonVariant.primary}
                  isDisabled={
                    !isValid || isSubmitting || isValidating || !isDirty
                  }
                >
                  Guardar
                </Button>
              </ActionGroup>
            </Form>
          </GridItem>
        </Grid>
      </CardBody>
    </Card>
  );
};
