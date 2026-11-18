import type React from "react";
import { useId } from "react";
import { Controller, useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";
import { Link } from "@tanstack/react-router";

import {
  ActionGroup,
  Alert,
  Breadcrumb,
  BreadcrumbItem,
  Button,
  Card,
  CardBody,
  Content,
  ContentVariants,
  Form,
  PageSection,
  Spinner,
  Switch,
} from "@patternfly/react-core";
import { DataView, DataViewTable } from "@patternfly/react-data-view";

import type { Preference } from "@app/client/xhandler";
import { DocumentTitle } from "@app/components/DocumentTitle";
import { FormSubmitError } from "@app/components/FormSubmitError";
import { GatedButton } from "@app/components/GatedButton";
import { TenantContextSelector } from "@app/components/TenantContextSelector";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";
import { useSubmitError } from "@app/hooks/useSubmitError";
import { useTenantRole } from "@app/hooks/useTenantRole";
import {
  useNotificationPreferencesMutation,
  useNotificationPreferencesQuery,
  withDefaultPreferences,
} from "@app/queries/xhandler/notifications";
import { getMutationErrorMessage } from "@app/utils/apiError";
import type { DataViewTh } from "@app/utils/dataViewTypes";
import { notificationKindLabel } from "@app/utils/notificationKinds";

import { preferencesSchema } from "./preferencesSchema";

/** Which notification kinds reach the organization by email and in the console (admin). */
export const NotificationPreferences: React.FC = () => {
  const tenantId = useSelectedTenantId();
  const { data, isLoading, error } = useNotificationPreferencesQuery();

  const body = (() => {
    if (isLoading) return <Spinner aria-label="Cargando preferencias" />;
    if (error || !data) {
      const message = getMutationErrorMessage({
        error,
        fallback: "No se pudieron cargar las preferencias",
      });
      return (
        <Alert variant="danger" isInline title={message.title}>
          {message.description}
        </Alert>
      );
    }
    return <PreferencesForm initial={withDefaultPreferences(data)} />;
  })();

  return (
    <>
      <DocumentTitle title="Preferencias de notificación" />
      <TenantContextSelector />
      <PageSection>
        <Breadcrumb>
          <BreadcrumbItem>
            <Link
              to="/organizaciones/$tenantId/notificaciones"
              params={{ tenantId }}
            >
              Notificaciones
            </Link>
          </BreadcrumbItem>
          <BreadcrumbItem isActive>Preferencias</BreadcrumbItem>
        </Breadcrumb>
        <Content component={ContentVariants.h1}>
          Preferencias de notificación
        </Content>
        <Content component={ContentVariants.p}>
          Elige qué avisos llegan por correo al correo de notificaciones de la
          organización y cuáles se muestran en la consola.
        </Content>
      </PageSection>
      <PageSection>
        <Card>
          <CardBody>{body}</CardBody>
        </Card>
      </PageSection>
    </>
  );
};

/** A preference as the form edits it: both channels set. */
const toValues = (pref: Preference) => ({
  kind: pref.kind,
  email: pref.email ?? true,
  in_app: pref.in_app ?? true,
});

interface IPreferencesFormProps {
  initial: Preference[];
}

const PreferencesForm: React.FC<IPreferencesFormProps> = ({ initial }) => {
  const formId = useId();
  const { addNotification } = useNotifications();
  const { can, disabledProps } = useTenantRole();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();
  const isReadOnly = !can("admin");
  const { control, handleSubmit, formState, reset } = useForm({
    resolver: yupResolver(preferencesSchema),
    defaultValues: {
      preferences: initial.map(toValues),
    },
  });

  const save = useNotificationPreferencesMutation({
    onSuccess: () =>
      addNotification({
        variant: "success",
        title: "Se guardaron las preferencias de notificación",
      }),
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: "No se pudieron guardar las preferencias",
        }),
      ),
  });

  const onSubmit = handleSubmit(({ preferences }) => {
    clearSubmitError();
    save.mutate(preferences, {
      onSuccess: (stored) =>
        reset({ preferences: withDefaultPreferences(stored).map(toValues) }),
    });
  });

  const columns: DataViewTh[] = [
    { cell: "Aviso", props: { width: 60 } },
    { cell: "Correo", props: { width: 20 } },
    { cell: "En la consola", props: { width: 20 } },
  ];

  const rows = initial.map((pref, index) => ({
    id: pref.kind,
    row: [
      { cell: notificationKindLabel(pref.kind) },
      {
        cell: (
          <Controller
            control={control}
            name={`preferences.${index}.email`}
            render={({ field }) => (
              <Switch
                id={`${formId}-${index}-email`}
                aria-label={`Correo: ${notificationKindLabel(pref.kind)}`}
                isChecked={field.value}
                onChange={(_event, checked) => field.onChange(checked)}
                isDisabled={isReadOnly}
              />
            )}
          />
        ),
      },
      {
        cell: (
          <Controller
            control={control}
            name={`preferences.${index}.in_app`}
            render={({ field }) => (
              <Switch
                id={`${formId}-${index}-in-app`}
                aria-label={`En la consola: ${notificationKindLabel(pref.kind)}`}
                isChecked={field.value}
                onChange={(_event, checked) => field.onChange(checked)}
                isDisabled={isReadOnly}
              />
            )}
          />
        ),
      },
    ],
  }));

  return (
    <Form id={formId} onSubmit={onSubmit}>
      <FormSubmitError error={submitError} />
      <DataView>
        <DataViewTable
          aria-label="Tabla de preferencias de notificación"
          columns={columns}
          rows={rows}
        />
      </DataView>
      <ActionGroup>
        <GatedButton
          type="submit"
          form={formId}
          isLoading={save.isPending}
          isDisabled={save.isPending || !formState.isDirty}
          {...disabledProps("admin")}
        >
          Guardar
        </GatedButton>
        <Button
          variant="link"
          onClick={() => {
            clearSubmitError();
            reset();
          }}
          isDisabled={!formState.isDirty}
        >
          Descartar cambios
        </Button>
      </ActionGroup>
    </Form>
  );
};
