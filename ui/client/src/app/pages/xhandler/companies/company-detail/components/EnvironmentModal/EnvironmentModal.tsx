import type React from "react";
import { useId } from "react";
import { Controller, useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";
import { mixed, object } from "yup";

import {
  Alert,
  Button,
  Form,
  FormGroup,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
  Radio,
} from "@patternfly/react-core";

import type { CompanyView } from "@app/client/xhandler";
import { FormSubmitError } from "@app/components/FormSubmitError";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSubmitError } from "@app/hooks/useSubmitError";
import {
  isSunatEnv,
  SUNAT_ENVS,
  type SunatEnv,
  useCompanyEnvironmentMutation,
} from "@app/queries/xhandler/companies";
import { getMutationErrorMessage } from "@app/utils/apiError";

/** Spanish names of the environments, with what each one is for. */
const SUNAT_ENV_OPTIONS: Record<
  SunatEnv,
  { label: string; description: string }
> = {
  fake: {
    label: "Simulado",
    description: "Un SUNAT simulado de la plataforma, para pruebas sin red.",
  },
  beta: {
    label: "Beta",
    description: "El ambiente de pruebas de SUNAT; los comprobantes no valen.",
  },
  production: {
    label: "Producción",
    description:
      "Comprobantes reales. Requiere RUC verificado, certificado vigente y usuario SOL.",
  },
};

const schema = object({
  sunat_env: mixed<SunatEnv>()
    .required("Elige un ambiente")
    .test("known", "Elige un ambiente", (value) => isSunatEnv(String(value))),
});

interface IEnvironmentModalProps {
  isOpen: boolean;
  company: CompanyView;
  onClose: () => void;
}

/** Moves the company to another SUNAT environment; the server refuses production until ready. */
export const EnvironmentModal: React.FC<IEnvironmentModalProps> = ({
  isOpen,
  company,
  onClose,
}) => (
  <Modal
    isOpen={isOpen}
    onClose={onClose}
    variant="small"
    aria-labelledby="environment-modal-title"
  >
    {/* Mounted only while open, so each opening starts from the company's environment. */}
    {isOpen ? <EnvironmentForm company={company} onClose={onClose} /> : null}
  </Modal>
);

interface IEnvironmentFormProps {
  company: CompanyView;
  onClose: () => void;
}

const EnvironmentForm: React.FC<IEnvironmentFormProps> = ({
  company,
  onClose,
}) => {
  const formId = useId();
  const { addNotification } = useNotifications();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();
  const companyEnv = company.sunat_env ?? "";
  const current: SunatEnv = isSunatEnv(companyEnv) ? companyEnv : "beta";

  const { control, handleSubmit, watch } = useForm({
    resolver: yupResolver(schema),
    defaultValues: { sunat_env: current },
  });
  const selected = watch("sunat_env");

  const change = useCompanyEnvironmentMutation(company.id ?? "", {
    onSuccess: (_data, env) =>
      addNotification({
        variant: "success",
        title: `La empresa pasó a ${SUNAT_ENV_OPTIONS[env].label}`,
      }),
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: "No se pudo cambiar el ambiente",
        }),
      ),
  });

  const onSubmit = handleSubmit(({ sunat_env }) => {
    clearSubmitError();
    change.mutate(sunat_env, { onSuccess: onClose });
  });

  return (
    <>
      <ModalHeader
        title="Cambiar ambiente SUNAT"
        labelId="environment-modal-title"
      />
      <ModalBody>
        <Form id={formId} onSubmit={onSubmit}>
          <FormSubmitError error={submitError} />
          <FormGroup
            role="radiogroup"
            label="Ambiente"
            isStack
            fieldId={formId}
          >
            <Controller
              control={control}
              name="sunat_env"
              render={({ field }) => (
                <>
                  {SUNAT_ENVS.map((env) => (
                    <Radio
                      key={env}
                      id={`${formId}-${env}`}
                      name={field.name}
                      label={SUNAT_ENV_OPTIONS[env].label}
                      description={SUNAT_ENV_OPTIONS[env].description}
                      isChecked={field.value === env}
                      onChange={() => field.onChange(env)}
                    />
                  ))}
                </>
              )}
            />
          </FormGroup>
          {selected === "production" && current !== "production" ? (
            <Alert
              variant="warning"
              isInline
              title="Los comprobantes que emitas en producción se envían a SUNAT y tienen valor legal."
            />
          ) : null}
        </Form>
      </ModalBody>
      <ModalFooter>
        <Button
          type="submit"
          form={formId}
          isLoading={change.isPending}
          isDisabled={change.isPending || selected === current}
        >
          Cambiar ambiente
        </Button>
        <Button variant="link" onClick={onClose}>
          Cancelar
        </Button>
      </ModalFooter>
    </>
  );
};
