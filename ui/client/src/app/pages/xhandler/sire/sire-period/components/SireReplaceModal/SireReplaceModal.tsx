import type React from "react";
import { useId } from "react";
import { Controller, useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";
import { boolean, object } from "yup";

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
} from "@patternfly/react-core";

import type { SirePeriodView } from "@app/client/xhandler";
import { FormSubmitError } from "@app/components/FormSubmitError";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSubmitError } from "@app/hooks/useSubmitError";
import {
  type ISirePeriodPath,
  sireReconciliation,
  useReplaceSireProposalMutation,
} from "@app/queries/xhandler/sire";
import { getMutationErrorMessage } from "@app/utils/apiError";

interface ISireReplaceModalProps {
  path: ISirePeriodPath;
  /** The period to replace; the modal is open while it is set. */
  period: SirePeriodView | null;
  onClose: () => void;
}

interface ISireReplaceValues {
  acknowledge_differences: boolean;
  confirm_not_received: boolean;
}

interface ISireReplaceContext {
  needsAcknowledge: boolean;
  needsConfirm: boolean;
}

// The confirmations the server asks for (`SireResource.replace`), required only when they apply.
const schema = object({
  acknowledge_differences: boolean()
    .required()
    .when("$needsAcknowledge", {
      is: true,
      then: (rule) =>
        rule.oneOf([true], "Confirma que aceptas las diferencias"),
    }),
  confirm_not_received: boolean()
    .required()
    .when("$needsConfirm", {
      is: true,
      then: (rule) =>
        rule.oneOf([true], "Confirma que verificaste el envío en SOL"),
    }),
});

/**
 * Submits the stored rows as the replacement of SUNAT's proposal (admins), with the explicit
 * confirmations the period needs: accepting the differences, and after an unknown outcome,
 * that SUNAT didn't get the earlier replacement.
 */
export const SireReplaceModal: React.FC<ISireReplaceModalProps> = ({
  path,
  period,
  onClose,
}) => (
  <Modal
    isOpen={period !== null}
    onClose={onClose}
    variant="medium"
    aria-labelledby="sire-replace-title"
  >
    {period ? (
      <SireReplaceForm path={path} period={period} onClose={onClose} />
    ) : null}
  </Modal>
);

interface ISireReplaceFormProps {
  path: ISirePeriodPath;
  period: SirePeriodView;
  onClose: () => void;
}

const SireReplaceForm: React.FC<ISireReplaceFormProps> = ({
  path,
  period,
  onClose,
}) => {
  const formId = useId();
  const { addNotification } = useNotifications();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();
  const replace = useReplaceSireProposalMutation({
    onSuccess: () =>
      addNotification({
        variant: "info",
        title: "Se envió el reemplazo a SUNAT",
        description: "Un trabajo sigue su respuesta.",
      }),
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: "No se pudo enviar el reemplazo",
        }),
      ),
  });

  const needsAcknowledge = sireReconciliation(period)?.matches !== true;
  const needsConfirm = period.status === "unknown";

  const {
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<ISireReplaceValues, ISireReplaceContext>({
    resolver: yupResolver(schema),
    context: { needsAcknowledge, needsConfirm },
    defaultValues: {
      acknowledge_differences: false,
      confirm_not_received: false,
    },
  });

  const onSubmit = (values: ISireReplaceValues) => {
    clearSubmitError();
    replace.mutate(
      {
        ...path,
        body: {
          acknowledge_differences:
            needsAcknowledge && values.acknowledge_differences,
          confirm_not_received: needsConfirm && values.confirm_not_received,
        },
      },
      { onSuccess: onClose },
    );
  };

  const helper = (message: string | undefined) =>
    message ? (
      <FormHelperText>
        <HelperText>
          <HelperTextItem variant="error">{message}</HelperTextItem>
        </HelperText>
      </FormHelperText>
    ) : null;

  return (
    <>
      <ModalHeader
        title="¿Reemplazar la propuesta de SUNAT?"
        labelId="sire-replace-title"
        titleIconVariant="warning"
      />
      <ModalBody>
        <Form
          id={formId}
          onSubmit={(event) => void handleSubmit(onSubmit)(event)}
        >
          <FormSubmitError error={submitError} />
          <Content component={ContentVariants.p}>
            Se enviará a SUNAT el registro {path.book.toUpperCase()} del periodo{" "}
            {path.period} armado con los comprobantes registrados en la
            plataforma, en lugar de la propuesta de SUNAT.
          </Content>
          {needsAcknowledge ? (
            <FormGroup fieldId={`${formId}-acknowledge`}>
              <Controller
                name="acknowledge_differences"
                control={control}
                render={({ field }) => (
                  <Checkbox
                    id={`${formId}-acknowledge`}
                    label="Revisé la conciliación y acepto las diferencias: lo que SUNAT tiene y la plataforma no, se quitará del registro."
                    isChecked={field.value}
                    onChange={(_event, checked) => field.onChange(checked)}
                  />
                )}
              />
              {helper(errors.acknowledge_differences?.message)}
            </FormGroup>
          ) : null}
          {needsConfirm ? (
            <FormGroup fieldId={`${formId}-confirm`}>
              <Controller
                name="confirm_not_received"
                control={control}
                render={({ field }) => (
                  <Checkbox
                    id={`${formId}-confirm`}
                    label="Verifiqué en SUNAT Operaciones en Línea que SUNAT no recibió el reemplazo anterior."
                    isChecked={field.value}
                    onChange={(_event, checked) => field.onChange(checked)}
                  />
                )}
              />
              {helper(errors.confirm_not_received?.message)}
            </FormGroup>
          ) : null}
        </Form>
      </ModalBody>
      <ModalFooter>
        <Button
          type="submit"
          form={formId}
          isLoading={replace.isPending}
          isDisabled={replace.isPending}
        >
          Enviar reemplazo
        </Button>
        <Button variant="link" onClick={onClose}>
          Volver
        </Button>
      </ModalFooter>
    </>
  );
};
