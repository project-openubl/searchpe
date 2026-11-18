import type React from "react";
import { useId } from "react";
import { useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";

import {
  Button,
  Form,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
} from "@patternfly/react-core";

import type { SeriesView } from "@app/client/xhandler";
import { ControlledTextField } from "@app/components/ControlledTextField";
import { FormSubmitError } from "@app/components/FormSubmitError";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSubmitError } from "@app/hooks/useSubmitError";
import { useSeriesUpdateMutation } from "@app/queries/xhandler/series";
import { getMutationErrorMessage } from "@app/utils/apiError";

import { seriesNextNumberSchema } from "./seriesForm";

interface ISeriesEditModalProps {
  companyId: string;
  /** The series edited; the modal is open while it is set. */
  series: SeriesView | null;
  onClose: () => void;
}

/** Moves a series' next number forward (to continue another system's numbering). */
export const SeriesEditModal: React.FC<ISeriesEditModalProps> = ({
  companyId,
  series,
  onClose,
}) => (
  <Modal
    isOpen={series !== null}
    onClose={onClose}
    variant="small"
    aria-labelledby="series-edit-modal-title"
  >
    {series ? (
      <SeriesEditForm companyId={companyId} series={series} onClose={onClose} />
    ) : null}
  </Modal>
);

interface ISeriesEditFormProps {
  companyId: string;
  series: SeriesView;
  onClose: () => void;
}

const SeriesEditForm: React.FC<ISeriesEditFormProps> = ({
  companyId,
  series,
  onClose,
}) => {
  const formId = useId();
  const { addNotification } = useNotifications();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();
  const current = series.next_number ?? 1;
  const { control, handleSubmit } = useForm({
    resolver: yupResolver(seriesNextNumberSchema(current)),
    defaultValues: { next_number: current },
    mode: "onTouched",
  });

  const update = useSeriesUpdateMutation(companyId, {
    onSuccess: (updated) =>
      addNotification({
        variant: "success",
        title: `La serie ${updated.serie ?? ""} continúa en el número ${updated.next_number?.toLocaleString("es-PE") ?? ""}`,
      }),
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: "No se pudo cambiar la serie",
        }),
      ),
  });

  const onSubmit = handleSubmit((values) => {
    clearSubmitError();
    update.mutate(
      {
        seriesId: series.id ?? "",
        body: { next_number: values.next_number },
      },
      { onSuccess: onClose },
    );
  });

  return (
    <>
      <ModalHeader
        title={`Editar la serie ${series.serie ?? ""}`}
        labelId="series-edit-modal-title"
      />
      <ModalBody>
        <Form id={formId} onSubmit={onSubmit}>
          <FormSubmitError error={submitError} />
          <ControlledTextField
            control={control}
            name="next_number"
            label="Siguiente número"
            isRequired
            inputMode="numeric"
            helperText="Solo avanza: los números ya usados no se vuelven a emitir."
          />
        </Form>
      </ModalBody>
      <ModalFooter>
        <Button
          type="submit"
          form={formId}
          isLoading={update.isPending}
          isDisabled={update.isPending}
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
