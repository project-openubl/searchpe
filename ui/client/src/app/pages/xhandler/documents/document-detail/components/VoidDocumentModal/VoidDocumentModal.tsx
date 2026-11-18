import type React from "react";
import { useId } from "react";
import { Controller, useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";

import {
  Button,
  Checkbox,
  Content,
  ContentVariants,
  Form,
  FormGroup,
  FormHelperText,
  HelperText,
  HelperTextItem,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
  TextArea,
  TextInput,
} from "@patternfly/react-core";

import type { DocumentView } from "@app/client/xhandler";
import { FormSubmitError } from "@app/components/FormSubmitError";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSubmitError } from "@app/hooks/useSubmitError";
import {
  needsReversalDate,
  useDocumentVoidMutation,
} from "@app/queries/xhandler/documents";
import { getMutationErrorMessage } from "@app/utils/apiError";

import {
  type IVoidDocumentValues,
  voidDocumentSchema,
} from "./voidDocumentSchema";

interface IVoidDocumentModalProps {
  document: DocumentView;
  onClose: () => void;
}

/** Voids an accepted, undelivered document: `202`, the document stays `voiding` until SUNAT answers. */
export const VoidDocumentModal: React.FC<IVoidDocumentModalProps> = ({
  document,
  onClose,
}) => {
  const formId = useId();
  const titleId = useId();
  const { addNotification } = useNotifications();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();
  const needsReversal = needsReversalDate(document);

  const {
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<IVoidDocumentValues>({
    resolver: yupResolver(voidDocumentSchema),
    context: { needsReversal },
    defaultValues: { motivo: "", notDelivered: false, reversalDate: "" },
  });

  const voidDocument = useDocumentVoidMutation({
    onSuccess: (data) =>
      addNotification({
        variant: "info",
        title: `Se envió la anulación de ${data.serie_numero ?? "el comprobante"}`,
        description:
          "El comprobante queda en anulación hasta que SUNAT responda; su estado se actualiza solo.",
      }),
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: "No se pudo anular el comprobante",
        }),
      ),
  });

  const onSubmit = (values: IVoidDocumentValues) => {
    clearSubmitError();
    voidDocument.mutate(
      {
        documentId: document.id ?? "",
        body: {
          motivo: values.motivo,
          not_delivered: values.notDelivered,
          reversal_communicated_at:
            needsReversal && values.reversalDate
              ? values.reversalDate
              : undefined,
        },
      },
      { onSuccess: onClose },
    );
  };

  return (
    <Modal isOpen variant="small" onClose={onClose} aria-labelledby={titleId}>
      <ModalHeader
        title={`Anular ${document.serie_numero ?? "comprobante"}`}
        labelId={titleId}
        titleIconVariant="warning"
      />
      <ModalBody>
        <Form id={formId} onSubmit={handleSubmit(onSubmit)}>
          <FormSubmitError error={submitError} />
          <Content component={ContentVariants.p}>
            Se enviará a SUNAT una comunicación de baja (o el resumen que
            corresponda). Solo procede si el comprador no recibió el
            comprobante; si lo recibió, emite una nota de crédito.
          </Content>
          <Controller
            control={control}
            name="motivo"
            render={({ field }) => (
              <FormGroup label="Motivo" isRequired fieldId={`${formId}-motivo`}>
                <TextArea
                  id={`${formId}-motivo`}
                  value={field.value}
                  onChange={(_event, value) => field.onChange(value)}
                  onBlur={field.onBlur}
                  validated={errors.motivo ? "error" : "default"}
                  resizeOrientation="vertical"
                />
                {errors.motivo ? (
                  <FormHelperText>
                    <HelperText>
                      <HelperTextItem variant="error">
                        {errors.motivo.message}
                      </HelperTextItem>
                    </HelperText>
                  </FormHelperText>
                ) : null}
              </FormGroup>
            )}
          />
          {needsReversal ? (
            <Controller
              control={control}
              name="reversalDate"
              render={({ field }) => (
                <FormGroup
                  label="Fecha en que se comunicó la reversión"
                  isRequired
                  fieldId={`${formId}-reversal`}
                >
                  <TextInput
                    id={`${formId}-reversal`}
                    type="date"
                    value={field.value}
                    onChange={(_event, value) => field.onChange(value)}
                    onBlur={field.onBlur}
                    validated={errors.reversalDate ? "error" : "default"}
                  />
                  {errors.reversalDate ? (
                    <FormHelperText>
                      <HelperText>
                        <HelperTextItem variant="error">
                          {errors.reversalDate.message}
                        </HelperTextItem>
                      </HelperText>
                    </FormHelperText>
                  ) : null}
                </FormGroup>
              )}
            />
          ) : null}
          <Controller
            control={control}
            name="notDelivered"
            render={({ field }) => (
              <FormGroup fieldId={`${formId}-not-delivered`}>
                <Checkbox
                  id={`${formId}-not-delivered`}
                  label="Confirmo que el comprador no recibió el comprobante"
                  isChecked={field.value}
                  onChange={(_event, checked) => field.onChange(checked)}
                />
                {errors.notDelivered ? (
                  <FormHelperText>
                    <HelperText>
                      <HelperTextItem variant="error">
                        {errors.notDelivered.message}
                      </HelperTextItem>
                    </HelperText>
                  </FormHelperText>
                ) : null}
              </FormGroup>
            )}
          />
        </Form>
      </ModalBody>
      <ModalFooter>
        <Button
          type="submit"
          form={formId}
          variant="primary"
          isLoading={voidDocument.isPending}
          isDisabled={voidDocument.isPending}
        >
          Anular
        </Button>
        <Button variant="link" onClick={onClose}>
          Cancelar
        </Button>
      </ModalFooter>
    </Modal>
  );
};
