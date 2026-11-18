import type React from "react";
import { useId } from "react";
import { Controller, useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";
import { object, string } from "yup";

import {
  ActionGroup,
  Form,
  FormGroup,
  FormHelperText,
  HelperText,
  HelperTextItem,
  TextArea,
  TextInput,
} from "@patternfly/react-core";

import type { Branding } from "@app/client/xhandler";
import { FormSubmitError } from "@app/components/FormSubmitError";
import { GatedButton } from "@app/components/GatedButton";
import type { IMutationErrorMessage } from "@app/utils/apiError";

interface IPartnerBrandingValues {
  sender_name: string;
  color: string;
  footer: string;
}

const schema = object({
  sender_name: string()
    .trim()
    .max(200, "El nombre tiene hasta 200 caracteres")
    .default(""),
  color: string()
    .trim()
    .matches(
      /^(#[0-9a-fA-F]{6})?$/,
      "Escribe el color como #RRGGBB, por ejemplo #1F6FB2",
    )
    .default(""),
  footer: string()
    .trim()
    .max(300, "El pie de página tiene hasta 300 caracteres")
    .default(""),
});

const toValues = (branding: Branding | undefined): IPartnerBrandingValues => ({
  sender_name: branding?.sender_name ?? "",
  color: branding?.color ?? "",
  footer: branding?.footer ?? "",
});

interface IPartnerBrandingFormProps {
  /** The current branding (`GET …/branding`), shown when the form mounts. */
  branding?: Branding;
  submitError: IMutationErrorMessage | null;
  isSaving: boolean;
  /** Disabled when the user isn't an admin of the partner. */
  gateProps: { isAriaDisabled?: boolean; tooltipProps?: { content: string } };
  onSave: (branding: Branding) => void;
}

/** Only the filled fields are sent: the PUT replaces the whole branding. */
const toBranding = (values: IPartnerBrandingValues): Branding => {
  const branding: Branding = {};
  if (values.sender_name.trim())
    branding.sender_name = values.sender_name.trim();
  if (values.color.trim()) branding.color = values.color.trim();
  if (values.footer.trim()) branding.footer = values.footer.trim();
  return branding;
};

/**
 * The partner's white-label branding for PDFs and emails, prefilled with the current one; saving
 * replaces it (empty fields are removed).
 */
export const PartnerBrandingForm: React.FC<IPartnerBrandingFormProps> = ({
  branding,
  submitError,
  isSaving,
  gateProps,
  onSave,
}) => {
  const formId = useId();
  const {
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<IPartnerBrandingValues>({
    resolver: yupResolver(schema),
    defaultValues: toValues(branding),
  });

  const helper = (error: string | undefined, text: string) => (
    <FormHelperText>
      <HelperText>
        <HelperTextItem variant={error ? "error" : "default"}>
          {error ?? text}
        </HelperTextItem>
      </HelperText>
    </FormHelperText>
  );

  return (
    <Form
      id={formId}
      onSubmit={(event) =>
        void handleSubmit((values) => onSave(toBranding(values)))(event)
      }
    >
      <FormSubmitError error={submitError} />
      <FormGroup label="Nombre del remitente" fieldId={`${formId}-sender`}>
        <Controller
          name="sender_name"
          control={control}
          render={({ field }) => (
            <TextInput
              id={`${formId}-sender`}
              value={field.value}
              onChange={(_event, value) => field.onChange(value)}
              onBlur={field.onBlur}
              validated={errors.sender_name ? "error" : "default"}
            />
          )}
        />
        {helper(
          errors.sender_name?.message,
          "El nombre con el que salen los correos a los clientes.",
        )}
      </FormGroup>
      <FormGroup label="Color principal" fieldId={`${formId}-color`}>
        <Controller
          name="color"
          control={control}
          render={({ field }) => (
            <TextInput
              id={`${formId}-color`}
              value={field.value}
              placeholder="#1F6FB2"
              onChange={(_event, value) => field.onChange(value)}
              onBlur={field.onBlur}
              validated={errors.color ? "error" : "default"}
            />
          )}
        />
        {helper(
          errors.color?.message,
          "El color de los PDF y correos, como #RRGGBB.",
        )}
      </FormGroup>
      <FormGroup label="Pie de página" fieldId={`${formId}-footer`}>
        <Controller
          name="footer"
          control={control}
          render={({ field }) => (
            <TextArea
              id={`${formId}-footer`}
              value={field.value}
              onChange={(_event, value) => field.onChange(value)}
              onBlur={field.onBlur}
              validated={errors.footer ? "error" : "default"}
              resizeOrientation="vertical"
            />
          )}
        />
        {helper(
          errors.footer?.message,
          "El texto al pie de los PDF y correos.",
        )}
      </FormGroup>
      <ActionGroup>
        <GatedButton
          type="submit"
          form={formId}
          isLoading={isSaving}
          isDisabled={isSaving}
          {...gateProps}
        >
          Guardar marca
        </GatedButton>
      </ActionGroup>
    </Form>
  );
};
