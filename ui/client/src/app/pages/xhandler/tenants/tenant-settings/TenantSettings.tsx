import type React from "react";
import { useId } from "react";
import { useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";

import {
  ActionGroup,
  Alert,
  Card,
  CardBody,
  CardTitle,
  Content,
  ContentVariants,
  DescriptionList,
  DescriptionListDescription,
  DescriptionListGroup,
  DescriptionListTerm,
  Form,
  PageSection,
  Spinner,
  Stack,
  StackItem,
} from "@patternfly/react-core";

import { ControlledTextField } from "@app/components/ControlledTextField";
import { DocumentTitle } from "@app/components/DocumentTitle";
import { FormSubmitError } from "@app/components/FormSubmitError";
import { GatedButton } from "@app/components/GatedButton";
import { TenantContextSelector } from "@app/components/TenantContextSelector";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";
import { useSubmitError } from "@app/hooks/useSubmitError";
import {
  TENANT_ROLE_LABELS,
  type TenantRole,
  useTenantRole,
} from "@app/hooks/useTenantRole";
import { useNotificationSettingsMutation } from "@app/queries/xhandler/notifications";
import { useTenantDetailQuery } from "@app/queries/xhandler/tenants";
import { getMutationErrorMessage } from "@app/utils/apiError";

import { notificationEmailSchema } from "./notificationEmailSchema";

const TENANT_STATUS_LABELS: Record<string, string> = {
  active: "Activa",
  exited: "Salida en curso",
  suspended: "Suspendida",
};

/** The organization's data and its notification address. */
export const TenantSettings: React.FC = () => {
  const tenantId = useSelectedTenantId();
  const { role } = useTenantRole();
  const { data: tenant, isLoading, error } = useTenantDetailQuery({ tenantId });

  const tenantBody = (() => {
    if (isLoading) return <Spinner aria-label="Cargando organización" />;
    if (error || !tenant) {
      const message = getMutationErrorMessage({
        error,
        fallback: "No se pudo cargar la organización",
      });
      return (
        <Alert variant="danger" isInline title={message.title}>
          {message.description}
        </Alert>
      );
    }
    return (
      <DescriptionList
        aria-label="Datos de la organización"
        columnModifier={{ md: "2Col" }}
      >
        <DescriptionListGroup>
          <DescriptionListTerm>Nombre</DescriptionListTerm>
          <DescriptionListDescription>
            {tenant.name ?? "-"}
          </DescriptionListDescription>
        </DescriptionListGroup>
        <DescriptionListGroup>
          <DescriptionListTerm>Estado</DescriptionListTerm>
          <DescriptionListDescription>
            {TENANT_STATUS_LABELS[tenant.status ?? ""] ?? tenant.status ?? "-"}
          </DescriptionListDescription>
        </DescriptionListGroup>
        <DescriptionListGroup>
          <DescriptionListTerm>Plan</DescriptionListTerm>
          <DescriptionListDescription>
            {tenant.plan ?? "-"}
          </DescriptionListDescription>
        </DescriptionListGroup>
        <DescriptionListGroup>
          <DescriptionListTerm>
            Conservación de comprobantes
          </DescriptionListTerm>
          <DescriptionListDescription>
            {tenant.retention_years !== undefined
              ? `${tenant.retention_years} años`
              : "-"}
          </DescriptionListDescription>
        </DescriptionListGroup>
        <DescriptionListGroup>
          <DescriptionListTerm>Mi rol</DescriptionListTerm>
          <DescriptionListDescription>
            {role ? (TENANT_ROLE_LABELS[role as TenantRole] ?? role) : "-"}
          </DescriptionListDescription>
        </DescriptionListGroup>
      </DescriptionList>
    );
  })();

  return (
    <>
      <DocumentTitle title="Configuración de la organización" />
      <TenantContextSelector />
      <PageSection>
        <Content component={ContentVariants.h1}>
          Configuración de la organización
        </Content>
        <Content component={ContentVariants.p}>
          Los datos de la organización y a dónde llegan sus avisos.
        </Content>
      </PageSection>
      <PageSection>
        <Stack hasGutter>
          <StackItem>
            <Card>
              <CardTitle component="h2">Organización</CardTitle>
              <CardBody>{tenantBody}</CardBody>
            </Card>
          </StackItem>
          <StackItem>
            <NotificationEmailCard />
          </StackItem>
        </Stack>
      </PageSection>
    </>
  );
};

/** Write-only: the API has no read for the address, so the field starts empty. */
const NotificationEmailCard: React.FC = () => {
  const formId = useId();
  const { addNotification } = useNotifications();
  const { disabledProps } = useTenantRole();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();
  const { control, handleSubmit } = useForm({
    resolver: yupResolver(notificationEmailSchema),
    defaultValues: { notification_email: "" },
    mode: "onTouched",
  });

  const save = useNotificationSettingsMutation({
    onSuccess: (_data, { notification_email }) =>
      addNotification({
        variant: "success",
        title: notification_email
          ? `Los avisos llegarán a ${notification_email}`
          : "Se quitó el correo de notificaciones",
      }),
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: "No se pudo guardar el correo de notificaciones",
        }),
      ),
  });

  const onSubmit = handleSubmit(({ notification_email }) => {
    clearSubmitError();
    save.mutate({ notification_email: notification_email ?? "" });
  });

  return (
    <Card>
      <CardTitle component="h2">Correo de notificaciones</CardTitle>
      <CardBody>
        <Form id={formId} onSubmit={onSubmit} maxWidth="600px">
          <FormSubmitError error={submitError} />
          <ControlledTextField
            control={control}
            name="notification_email"
            label="Correo"
            type="email"
            helperText="El correo actual no se muestra. Escribe el nuevo, o déjalo vacío para quitarlo."
          />
          <ActionGroup>
            <GatedButton
              type="submit"
              form={formId}
              isLoading={save.isPending}
              isDisabled={save.isPending}
              {...disabledProps("admin")}
            >
              Guardar
            </GatedButton>
          </ActionGroup>
        </Form>
      </CardBody>
    </Card>
  );
};
