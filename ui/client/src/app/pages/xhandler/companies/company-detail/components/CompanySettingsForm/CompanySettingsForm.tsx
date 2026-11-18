import type React from "react";
import { useId } from "react";
import { Controller, useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";

import {
  ActionGroup,
  Button,
  Card,
  CardBody,
  Form,
  FormGroup,
  FormSection,
  FormSelect,
  FormSelectOption,
  Switch,
} from "@patternfly/react-core";

import type { CompanyView } from "@app/client/xhandler";
import { ControlledTextField } from "@app/components/ControlledTextField";
import { FormSubmitError } from "@app/components/FormSubmitError";
import { GatedButton } from "@app/components/GatedButton";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSubmitError } from "@app/hooks/useSubmitError";
import { useTenantRole } from "@app/hooks/useTenantRole";
import {
  PDF_FORMATS,
  useCompanySettingsMutation,
} from "@app/queries/xhandler/companies";
import { getMutationErrorMessage } from "@app/utils/apiError";

import {
  companySettingsSchema,
  type ICompanySettingsValues,
  PDF_FORMAT_LABELS,
  settingsChanges,
  settingsDefaults,
} from "./companySettings";

interface ICompanySettingsFormProps {
  company: CompanyView;
}

/** The company's editable settings (`PUT …/settings`, admin); sends only what changed. */
export const CompanySettingsForm: React.FC<ICompanySettingsFormProps> = ({
  company,
}) => {
  const formId = useId();
  const { addNotification } = useNotifications();
  const { can, disabledProps } = useTenantRole();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();
  const isReadOnly = !can("admin");

  const { control, handleSubmit, formState, reset } =
    useForm<ICompanySettingsValues>({
      resolver: yupResolver(companySettingsSchema),
      defaultValues: settingsDefaults(company),
      mode: "onTouched",
    });

  const save = useCompanySettingsMutation(company.id ?? "", {
    onSuccess: () =>
      addNotification({
        variant: "success",
        title: "Se guardó la configuración de la empresa",
      }),
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: "No se pudo guardar la configuración",
        }),
      ),
  });

  const onSubmit = handleSubmit((values) => {
    clearSubmitError();
    save.mutate(settingsChanges({ values, dirty: formState.dirtyFields }), {
      onSuccess: (data) => reset(settingsDefaults(data)),
    });
  });

  return (
    <Card>
      <CardBody>
        <Form id={formId} onSubmit={onSubmit} maxWidth="720px">
          <FormSubmitError error={submitError} />
          <FormSection title="Datos de la empresa" titleElement="h2">
            <ControlledTextField
              control={control}
              name="nombre_comercial"
              label="Nombre comercial"
              isDisabled={isReadOnly}
            />
            <ControlledTextField
              control={control}
              name="direccion"
              label="Dirección"
              isDisabled={isReadOnly}
            />
            <ControlledTextField
              control={control}
              name="email"
              label="Correo"
              type="email"
              helperText="Se usa como remitente y respuesta de los correos a tus clientes."
              isDisabled={isReadOnly}
            />
          </FormSection>
          <FormSection title="Comprobantes" titleElement="h2">
            <Controller
              control={control}
              name="pdf_format"
              render={({ field }) => (
                <FormGroup label="Formato del PDF" fieldId={`${formId}-pdf`}>
                  <FormSelect
                    id={`${formId}-pdf`}
                    value={field.value}
                    onChange={(_event, value) => field.onChange(value)}
                    isDisabled={isReadOnly}
                  >
                    {PDF_FORMATS.map((format) => (
                      <FormSelectOption
                        key={format}
                        value={format}
                        label={PDF_FORMAT_LABELS[format]}
                      />
                    ))}
                  </FormSelect>
                </FormGroup>
              )}
            />
            <Controller
              control={control}
              name="delivered_on_emit_default"
              render={({ field }) => (
                <Switch
                  id={`${formId}-delivered`}
                  label="Marcar como entregado al emitir"
                  isChecked={field.value}
                  onChange={(_event, checked) => field.onChange(checked)}
                  isDisabled={isReadOnly}
                />
              )}
            />
          </FormSection>
          <FormSection title="Correo al cliente" titleElement="h2">
            <Controller
              control={control}
              name="email_on_accept"
              render={({ field }) => (
                <Switch
                  id={`${formId}-email-on-accept`}
                  label="Enviar el comprobante por correo cuando SUNAT lo acepte"
                  isChecked={field.value}
                  onChange={(_event, checked) => field.onChange(checked)}
                  isDisabled={isReadOnly}
                />
              )}
            />
            <ControlledTextField
              control={control}
              name="email_subject"
              label="Asunto"
              helperText="Vacío usa el asunto predeterminado."
              isDisabled={isReadOnly}
            />
            <ControlledTextField
              control={control}
              name="email_body"
              label="Mensaje"
              helperText="Vacío usa el mensaje predeterminado."
              isMultiline
              isDisabled={isReadOnly}
            />
          </FormSection>
          <FormSection title="Libro de Reclamaciones" titleElement="h2">
            <ControlledTextField
              control={control}
              name="libro_reclamaciones_url"
              label="Libro de Reclamaciones virtual"
              inputMode="url"
              helperText="Dirección https de tu Libro de Reclamaciones; el portal del comprador la muestra. Vacío la quita."
              isDisabled={isReadOnly}
            />
          </FormSection>
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
                reset(settingsDefaults(company));
              }}
              isDisabled={!formState.isDirty}
            >
              Descartar cambios
            </Button>
          </ActionGroup>
        </Form>
      </CardBody>
    </Card>
  );
};
