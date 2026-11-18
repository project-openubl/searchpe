import type React from "react";
import { useRef, useState } from "react";
import { Controller, useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";
import { number, object, string } from "yup";

import {
  ActionGroup,
  Button,
  Form,
  FormGroup,
  FormHelperText,
  FormSelect,
  FormSelectOption,
  HelperText,
  HelperTextItem,
  TextInput,
} from "@patternfly/react-core";

import type { ICaptchaConfig } from "@openubl-ui/common";

import type { OseLookupRequest } from "@app/client/xhandler";
import { FormSubmitError } from "@app/components/FormSubmitError";
import type { IMutationErrorMessage } from "@app/utils/apiError";

import { CaptchaWidget, type ICaptchaWidgetHandle } from "./CaptchaWidget";

/** The comprobantes an OSE validates (RS 117-2017): facturas, boletas and their notas. */
const TIPOS_COMPROBANTE: { value: string; label: string }[] = [
  { value: "01", label: "01 - Factura" },
  { value: "03", label: "03 - Boleta de venta" },
  { value: "07", label: "07 - Nota de crédito" },
  { value: "08", label: "08 - Nota de débito" },
];

interface IOseLookupFormValues {
  ruc: string;
  tipo_comprobante: string;
  serie: string;
  numero: number;
  captcha: string;
}

const schema = object({
  ruc: string()
    .trim()
    .required("Ingresa el RUC del emisor")
    .matches(/^\d{11}$/, "El RUC tiene 11 dígitos"),
  tipo_comprobante: string().required("Elige el tipo de comprobante"),
  serie: string()
    .trim()
    .uppercase()
    .required("Ingresa la serie")
    .matches(/^[A-Z0-9]{4}$/, "La serie tiene 4 caracteres, como F001"),
  numero: number()
    .typeError("Ingresa el número")
    .required("Ingresa el número")
    .integer("El número es un entero")
    .min(1, "El número es mayor que cero")
    .max(99999999, "El número tiene hasta 8 dígitos"),
  captcha: string().trim().required("Completa la verificación"),
});

interface IOseLookupFormProps {
  formId: string;
  submitError: IMutationErrorMessage | null;
  isSubmitting: boolean;
  onSubmit: (request: OseLookupRequest) => void;
  /** The provider's widget; without it the token is typed (xhandler's CAPTCHA test mode). */
  captcha?: ICaptchaConfig;
}

/** The OSE validity query's form: the printed data of a comprobante plus the CAPTCHA. */
export const OseLookupForm: React.FC<IOseLookupFormProps> = ({
  formId,
  submitError,
  isSubmitting,
  onSubmit,
  captcha,
}) => {
  const captchaRef = useRef<ICaptchaWidgetHandle>(null);
  const [captchaFailed, setCaptchaFailed] = useState(false);
  const {
    control,
    handleSubmit,
    setValue,
    formState: { errors, isSubmitted },
  } = useForm<IOseLookupFormValues>({
    resolver: yupResolver(schema),
    defaultValues: {
      ruc: "",
      tipo_comprobante: "01",
      serie: "",
      captcha: "",
    },
  });

  const helper = (message: string | undefined, fieldId: string) =>
    message ? (
      <FormHelperText>
        <HelperText id={`${fieldId}-helper`}>
          <HelperTextItem variant="error">{message}</HelperTextItem>
        </HelperText>
      </FormHelperText>
    ) : null;

  const setCaptchaToken = (token: string) =>
    setValue("captcha", token, { shouldValidate: isSubmitted });

  const captchaHelper = errors.captcha ? (
    helper(errors.captcha.message, `${formId}-captcha`)
  ) : captchaFailed ? (
    helper(
      "No se pudo completar la verificación. Inténtalo de nuevo.",
      `${formId}-captcha`,
    )
  ) : (
    <FormHelperText>
      <HelperText id={`${formId}-captcha-helper`}>
        <HelperTextItem>
          La verificación (CAPTCHA) evita consultas automáticas.
        </HelperTextItem>
      </HelperText>
    </FormHelperText>
  );

  return (
    <Form
      id={formId}
      onSubmit={(event) =>
        void handleSubmit((values) => {
          onSubmit(values);
          // A token verifies once: whatever the answer, the next query needs a new one.
          if (captcha) {
            setValue("captcha", "");
            captchaRef.current?.reset();
          }
        })(event)
      }
    >
      <FormSubmitError error={submitError} />
      <FormGroup label="RUC del emisor" isRequired fieldId={`${formId}-ruc`}>
        <Controller
          name="ruc"
          control={control}
          render={({ field }) => (
            <TextInput
              id={`${formId}-ruc`}
              inputMode="numeric"
              value={field.value}
              onChange={(_event, value) => field.onChange(value)}
              onBlur={field.onBlur}
              validated={errors.ruc ? "error" : "default"}
            />
          )}
        />
        {helper(errors.ruc?.message, `${formId}-ruc`)}
      </FormGroup>
      <FormGroup
        label="Tipo de comprobante"
        isRequired
        fieldId={`${formId}-tipo`}
      >
        <Controller
          name="tipo_comprobante"
          control={control}
          render={({ field }) => (
            <FormSelect
              id={`${formId}-tipo`}
              value={field.value}
              onChange={(_event, value) => field.onChange(value)}
            >
              {TIPOS_COMPROBANTE.map((tipo) => (
                <FormSelectOption
                  key={tipo.value}
                  value={tipo.value}
                  label={tipo.label}
                />
              ))}
            </FormSelect>
          )}
        />
        {helper(errors.tipo_comprobante?.message, `${formId}-tipo`)}
      </FormGroup>
      <FormGroup label="Serie" isRequired fieldId={`${formId}-serie`}>
        <Controller
          name="serie"
          control={control}
          render={({ field }) => (
            <TextInput
              id={`${formId}-serie`}
              value={field.value}
              onChange={(_event, value) => field.onChange(value.toUpperCase())}
              onBlur={field.onBlur}
              validated={errors.serie ? "error" : "default"}
            />
          )}
        />
        {helper(errors.serie?.message, `${formId}-serie`)}
      </FormGroup>
      <FormGroup label="Número" isRequired fieldId={`${formId}-numero`}>
        <Controller
          name="numero"
          control={control}
          render={({ field }) => (
            <TextInput
              id={`${formId}-numero`}
              inputMode="numeric"
              value={field.value === undefined ? "" : String(field.value)}
              onChange={(_event, value) =>
                field.onChange(value === "" ? undefined : value)
              }
              onBlur={field.onBlur}
              validated={errors.numero ? "error" : "default"}
            />
          )}
        />
        {helper(errors.numero?.message, `${formId}-numero`)}
      </FormGroup>
      {captcha ? (
        <FormGroup
          label="Verificación"
          isRequired
          role="group"
          fieldId={`${formId}-captcha`}
          aria-describedby={`${formId}-captcha-helper`}
        >
          <CaptchaWidget
            ref={captchaRef}
            id={`${formId}-captcha`}
            config={captcha}
            onVerify={(token) => {
              setCaptchaFailed(false);
              setCaptchaToken(token);
            }}
            onExpire={() => setCaptchaToken("")}
            onError={() => {
              setCaptchaFailed(true);
              setCaptchaToken("");
            }}
          />
          {captchaHelper}
        </FormGroup>
      ) : (
        // No site key (development, e2e): the token is typed; xhandler's test mode takes one.
        <FormGroup
          label="Código de verificación"
          isRequired
          fieldId={`${formId}-captcha`}
        >
          <Controller
            name="captcha"
            control={control}
            render={({ field }) => (
              <TextInput
                id={`${formId}-captcha`}
                value={field.value}
                onChange={(_event, value) => field.onChange(value)}
                onBlur={field.onBlur}
                validated={errors.captcha ? "error" : "default"}
                aria-describedby={`${formId}-captcha-helper`}
              />
            )}
          />
          {captchaHelper}
        </FormGroup>
      )}
      <ActionGroup>
        <Button
          type="submit"
          form={formId}
          isLoading={isSubmitting}
          isDisabled={isSubmitting}
        >
          Consultar
        </Button>
      </ActionGroup>
    </Form>
  );
};
