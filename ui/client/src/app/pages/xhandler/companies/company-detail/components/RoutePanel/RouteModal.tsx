import type React from "react";
import { useId } from "react";
import { Controller, useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";

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

import type { RouteView } from "@app/client/xhandler";
import { ControlledTextField } from "@app/components/ControlledTextField";
import { FormSubmitError } from "@app/components/FormSubmitError";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSubmitError } from "@app/hooks/useSubmitError";
import {
  SUBMISSION_ROUTES,
  useCompanyRouteMutation,
} from "@app/queries/xhandler/companies";
import { getMutationErrorMessage } from "@app/utils/apiError";

import {
  type IRouteFormValues,
  routeDefaults,
  routeSchema,
  SUBMISSION_ROUTE_LABELS,
  toRouteChange,
} from "./routeForm";

interface IRouteModalProps {
  isOpen: boolean;
  companyId: string;
  route: RouteView | undefined;
  onClose: () => void;
}

/** Chooses where the company's documents go: SUNAT, or a third-party OSE with its credentials. */
export const RouteModal: React.FC<IRouteModalProps> = ({
  isOpen,
  companyId,
  route,
  onClose,
}) => (
  <Modal
    isOpen={isOpen}
    onClose={onClose}
    variant="medium"
    aria-labelledby="route-modal-title"
  >
    {isOpen ? (
      <RouteForm companyId={companyId} route={route} onClose={onClose} />
    ) : null}
  </Modal>
);

interface IRouteFormProps {
  companyId: string;
  route: RouteView | undefined;
  onClose: () => void;
}

const RouteForm: React.FC<IRouteFormProps> = ({
  companyId,
  route,
  onClose,
}) => {
  const formId = useId();
  const { addNotification } = useNotifications();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();
  const { control, handleSubmit, watch } = useForm<IRouteFormValues>({
    resolver: yupResolver(routeSchema),
    defaultValues: routeDefaults(route),
    mode: "onTouched",
  });
  const selected = watch("submission_route");

  const save = useCompanyRouteMutation(companyId, {
    onSuccess: () =>
      addNotification({
        variant: "success",
        title: "Se guardó la ruta de envío",
      }),
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: "No se pudo cambiar la ruta de envío",
        }),
      ),
  });

  const onSubmit = handleSubmit((values) => {
    clearSubmitError();
    save.mutate(toRouteChange(values), { onSuccess: onClose });
  });

  return (
    <>
      <ModalHeader title="Ruta de envío" labelId="route-modal-title" />
      <ModalBody>
        <Form id={formId} onSubmit={onSubmit} autoComplete="off">
          <FormSubmitError error={submitError} />
          <FormGroup role="radiogroup" label="Enviar" isStack fieldId={formId}>
            <Controller
              control={control}
              name="submission_route"
              render={({ field }) => (
                <>
                  {SUBMISSION_ROUTES.map((value) => (
                    <Radio
                      key={value}
                      id={`${formId}-${value}`}
                      name={field.name}
                      label={SUBMISSION_ROUTE_LABELS[value]}
                      isChecked={field.value === value}
                      onChange={() => field.onChange(value)}
                    />
                  ))}
                </>
              )}
            />
          </FormGroup>
          {selected === "sunat" && route?.ose_obligated ? (
            <Alert
              variant="warning"
              isInline
              title="SUNAT designó a esta empresa como obligada a emitir a través de un OSE."
            />
          ) : null}
          {selected === "ose" ? (
            <>
              <ControlledTextField
                control={control}
                name="name"
                label="Nombre del OSE"
                isRequired
              />
              <ControlledTextField
                control={control}
                name="invoice_url"
                label="URL de comprobantes"
                type="url"
                isRequired
              />
              <ControlledTextField
                control={control}
                name="perception_url"
                label="URL de percepciones y retenciones"
                type="url"
              />
              <ControlledTextField
                control={control}
                name="username"
                label="Usuario del OSE"
                isRequired
                autoComplete="off"
              />
              <ControlledTextField
                control={control}
                name="password"
                label="Clave del OSE"
                type="password"
                isRequired
                autoComplete="new-password"
                helperText="Se guarda cifrada y no se vuelve a mostrar."
              />
            </>
          ) : null}
        </Form>
      </ModalBody>
      <ModalFooter>
        <Button
          type="submit"
          form={formId}
          isLoading={save.isPending}
          isDisabled={save.isPending}
        >
          Guardar
        </Button>
        <Button variant="link" onClick={onClose}>
          Cancelar
        </Button>
      </ModalFooter>
    </>
  );
};
