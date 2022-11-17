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
  EmptyStateIcon,
  EmptyStateVariant,
  Form,
  FormGroup,
  Grid,
  GridItem,
  Select,
  SelectVariant,
  TextInput,
  Title,
} from "@patternfly/react-core";
import { UserCircleIcon } from "@patternfly/react-icons";

import { useForm, Controller, FieldValues } from "react-hook-form";
import { yupResolver } from "@hookform/resolvers/yup";
import { object, string } from "yup";

import { AxiosError } from "axios";
import { useConfirmationContext } from "@project-openubl/lib-ui";

import { useWhoAmIQuery } from "queries/whoami";
import { useUpdateCurrentUserProfileMutation } from "queries/currentUser";

import {
  getAxiosErrorMessage,
  getValidatedFromError,
  getValidatedFromErrorTouched,
} from "utils/modelUtils";
import { User } from "api/models";

export const Overview: React.FC = () => {
  const confirmation = useConfirmationContext();

  const whoAmI = useWhoAmIQuery();

  // Form

  const validationSchema = object().shape({
    username: string().trim().required().min(3).max(250),
    fullName: string().trim().max(250),
  });

  const {
    handleSubmit,
    getFieldState,
    formState: { errors, isSubmitting, isValidating, isValid, isDirty },
    control,
  } = useForm({
    defaultValues: {
      username: whoAmI.data?.username || "",
      fullName: whoAmI.data?.fullName || "",
    },
    resolver: yupResolver(validationSchema),
    mode: "onChange",
  });

  const onSubmit = (formValues: FieldValues) => {
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
            <Card isHoverable isCompact>
              <Bullseye>
                <EmptyState variant={EmptyStateVariant.xs}>
                  <EmptyStateIcon icon={UserCircleIcon} />
                  <Title headingLevel="h2" size="md">
                    Profile picture
                  </Title>
                </EmptyState>
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
              <FormGroup
                label="Username"
                fieldId="username"
                isRequired={true}
                validated={getValidatedFromError(errors.username)}
                helperTextInvalid={errors?.username?.message}
              >
                <Controller
                  control={control}
                  name="username"
                  render={({
                    field: { onChange, value, name },
                    fieldState: { isTouched, error },
                  }) => (
                    <TextInput
                      onChange={onChange}
                      type="text"
                      name={name}
                      aria-label="username"
                      aria-describedby="username"
                      isRequired={true}
                      value={value}
                      validated={getValidatedFromErrorTouched(error, isTouched)}
                    />
                  )}
                />
              </FormGroup>
              <FormGroup
                label="Full name"
                fieldId="fullName"
                isRequired={true}
                validated={getValidatedFromError(errors.fullName)}
                helperTextInvalid={errors?.fullName?.message}
              >
                <Controller
                  control={control}
                  name="fullName"
                  render={({
                    field: { onChange, value, name },
                    fieldState: { isTouched, error },
                  }) => (
                    <TextInput
                      onChange={onChange}
                      type="text"
                      name={name}
                      aria-label="full-name"
                      aria-describedby="full-name"
                      isRequired={true}
                      value={value}
                      validated={getValidatedFromErrorTouched(error, isTouched)}
                    />
                  )}
                />
              </FormGroup>
              <FormGroup
                label="Permissions"
                fieldId="permissions"
                isRequired={false}
              >
                <Select
                  variant={SelectVariant.typeaheadMulti}
                  aria-label="permissions"
                  onToggle={() => {}}
                  selections={whoAmI.data?.permissions}
                  aria-labelledby="permissions"
                  isDisabled={true}
                >
                  {[]}
                </Select>
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
