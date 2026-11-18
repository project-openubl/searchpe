import type React from "react";
import { useId, useState } from "react";
import { Controller, useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";

import {
  Button,
  Form,
  FormGroup,
  FormSelect,
  FormSelectOption,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
} from "@patternfly/react-core";

import { ControlledTextField } from "@app/components/ControlledTextField";
import { FormSubmitError } from "@app/components/FormSubmitError";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSubmitError } from "@app/hooks/useSubmitError";
import { useSeriesCreateMutation } from "@app/queries/xhandler/series";
import { getMutationErrorMessage } from "@app/utils/apiError";

import { seriesSchema, TIPOS_COMPROBANTE } from "./seriesForm";

interface ISeriesCreateModalProps {
  isOpen: boolean;
  companyId: string;
  onClose: () => void;
}

/** A new series in the company's current environment; a migrated one continues its numbering. */
export const SeriesCreateModal: React.FC<ISeriesCreateModalProps> = ({
  isOpen,
  companyId,
  onClose,
}) => (
  <Modal
    isOpen={isOpen}
    onClose={onClose}
    variant="small"
    aria-labelledby="series-modal-title"
  >
    {isOpen ? (
      <SeriesCreateForm companyId={companyId} onClose={onClose} />
    ) : null}
  </Modal>
);

interface ISeriesCreateFormProps {
  companyId: string;
  onClose: () => void;
}

const SeriesCreateForm: React.FC<ISeriesCreateFormProps> = ({
  companyId,
  onClose,
}) => {
  const formId = useId();
  const { addNotification } = useNotifications();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();
  // One key per opening of the form: a retry of the same submission reuses it.
  const [idempotencyKey] = useState(() => crypto.randomUUID());
  const { control, handleSubmit } = useForm({
    resolver: yupResolver(seriesSchema),
    defaultValues: { tipo_comprobante: "01", serie: "" },
    mode: "onTouched",
  });

  const create = useSeriesCreateMutation(companyId, {
    onSuccess: (series) =>
      addNotification({
        variant: "success",
        title: `Se creó la serie ${series.serie ?? ""}`.trim(),
      }),
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: "No se pudo crear la serie",
        }),
      ),
  });

  const onSubmit = handleSubmit((values) => {
    clearSubmitError();
    create.mutate(
      {
        body: {
          tipo_comprobante: values.tipo_comprobante,
          serie: values.serie,
          next_number: values.next_number,
        },
        idempotencyKey,
      },
      { onSuccess: onClose },
    );
  });

  return (
    <>
      <ModalHeader title="Nueva serie" labelId="series-modal-title" />
      <ModalBody>
        <Form id={formId} onSubmit={onSubmit}>
          <FormSubmitError error={submitError} />
          <Controller
            control={control}
            name="tipo_comprobante"
            render={({ field }) => (
              <FormGroup
                label="Tipo de comprobante"
                isRequired
                fieldId={`${formId}-tipo`}
              >
                <FormSelect
                  id={`${formId}-tipo`}
                  value={field.value}
                  onChange={(_event, value) => field.onChange(value)}
                >
                  {Object.entries(TIPOS_COMPROBANTE).map(([code, label]) => (
                    <FormSelectOption
                      key={code}
                      value={code}
                      label={`${code} - ${label}`}
                    />
                  ))}
                </FormSelect>
              </FormGroup>
            )}
          />
          <ControlledTextField
            control={control}
            name="serie"
            label="Serie"
            isRequired
            placeholder="F002"
          />
          <ControlledTextField
            control={control}
            name="next_number"
            label="Siguiente número"
            inputMode="numeric"
            helperText="Si vienes de otro sistema, continúa su numeración; por defecto empieza en 1."
          />
        </Form>
      </ModalBody>
      <ModalFooter>
        <Button
          type="submit"
          form={formId}
          isLoading={create.isPending}
          isDisabled={create.isPending}
        >
          Crear serie
        </Button>
        <Button variant="link" onClick={onClose}>
          Cancelar
        </Button>
      </ModalFooter>
    </>
  );
};
