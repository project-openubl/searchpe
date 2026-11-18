import type React from "react";
import { useId, useState } from "react";
import { Controller, useForm } from "react-hook-form";

import { yupResolver } from "@hookform/resolvers/yup";

import {
  Alert,
  Button,
  Checkbox,
  ClipboardCopy,
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

import { ControlledTextField } from "@app/components/ControlledTextField";
import { FormSubmitError } from "@app/components/FormSubmitError";
import { useNotifications } from "@app/hooks/useNotifications";
import { useSubmitError } from "@app/hooks/useSubmitError";
import {
  useWebhookCreateMutation,
  WEBHOOK_EVENT_TYPES,
} from "@app/queries/xhandler/webhooks";
import { getMutationErrorMessage } from "@app/utils/apiError";

import { WEBHOOK_EVENT_LABELS } from "../../utils/webhookEvents";
import { toEvents, webhookSchema } from "./webhookForm";

const EVENT_OPTIONS = ["*", ...WEBHOOK_EVENT_TYPES] as const;

interface ICreateWebhookModalProps {
  isOpen: boolean;
  onClose: () => void;
}

/**
 * Subscribes a URL to events. On success the signing secret is shown here once (DESIGN.md §12):
 * the API never returns it again.
 */
export const CreateWebhookModal: React.FC<ICreateWebhookModalProps> = ({
  isOpen,
  onClose,
}) => (
  <Modal
    isOpen={isOpen}
    onClose={onClose}
    variant="medium"
    aria-labelledby="create-webhook-modal-title"
  >
    {isOpen ? <CreateWebhookForm onClose={onClose} /> : null}
  </Modal>
);

interface ICreateWebhookFormProps {
  onClose: () => void;
}

const CreateWebhookForm: React.FC<ICreateWebhookFormProps> = ({ onClose }) => {
  const formId = useId();
  const { addNotification } = useNotifications();
  const { submitError, setSubmitError, clearSubmitError } = useSubmitError();
  // The one-time secret is shown from this state; the mutation (gcTime 0) drops its copy
  // once the modal closes.
  // Undefined until created; null when the answer was a replay that no longer carries it.
  const [secret, setSecret] = useState<string | null | undefined>(undefined);
  // One per opening of the modal: a retry of the same submission is the same request.
  const [idempotencyKey] = useState(() => crypto.randomUUID());
  const { control, handleSubmit } = useForm({
    resolver: yupResolver(webhookSchema),
    defaultValues: { url: "", events: ["*"] },
    mode: "onTouched",
  });

  const create = useWebhookCreateMutation({
    onSuccess: (webhook) =>
      addNotification({
        variant: "success",
        title: `Se creó el webhook ${webhook.url ?? ""}`.trim(),
      }),
    onError: (error) =>
      setSubmitError(
        getMutationErrorMessage({
          error,
          fallback: "No se pudo crear el webhook",
        }),
      ),
  });

  const onSubmit = handleSubmit((values) => {
    clearSubmitError();
    create.mutate(
      {
        body: { url: values.url, events: toEvents(values.events) },
        idempotencyKey,
      },
      { onSuccess: (webhook) => setSecret(webhook.secret ?? null) },
    );
  });

  if (secret === null) {
    return (
      <>
        <ModalHeader
          title="Webhook creado"
          labelId="create-webhook-modal-title"
        />
        <ModalBody>
          <Alert
            variant="info"
            isInline
            title="El secreto ya se mostró al crear el webhook"
          >
            Esta solicitud repitió una anterior que ya creó el webhook, así que
            el secreto no se vuelve a mostrar. Si no lo copiaste, elimina el
            webhook y créalo de nuevo.
          </Alert>
        </ModalBody>
        <ModalFooter>
          <Button onClick={onClose}>Listo</Button>
        </ModalFooter>
      </>
    );
  }

  if (secret !== undefined) {
    return (
      <>
        <ModalHeader
          title="Webhook creado"
          labelId="create-webhook-modal-title"
        />
        <ModalBody>
          <Alert
            variant="warning"
            isInline
            title="Copia el secreto ahora: no se volverá a mostrar."
          >
            Úsalo para verificar la firma X-Xhandler-Signature de cada envío.
          </Alert>
          <ClipboardCopy
            isReadOnly
            hoverTip="Copiar"
            clickTip="Copiado"
            textAriaLabel="Secreto del webhook"
            toggleAriaLabel="Mostrar el secreto"
          >
            {secret}
          </ClipboardCopy>
        </ModalBody>
        <ModalFooter>
          <Button onClick={onClose}>Listo</Button>
        </ModalFooter>
      </>
    );
  }

  return (
    <>
      <ModalHeader title="Nuevo webhook" labelId="create-webhook-modal-title" />
      <ModalBody>
        <Form id={formId} onSubmit={onSubmit}>
          <FormSubmitError error={submitError} />
          <ControlledTextField
            control={control}
            name="url"
            label="URL"
            type="url"
            isRequired
            placeholder="https://erp.example.com/webhooks/xhandler"
            helperText="https en el puerto 443 u 8443, con una dirección pública."
          />
          <Controller
            control={control}
            name="events"
            render={({ field, fieldState }) => {
              const picked: string[] = field.value ?? [];
              const toggle = (event: string, checked: boolean) =>
                field.onChange(
                  checked
                    ? [...picked, event]
                    : picked.filter((value) => value !== event),
                );
              return (
                <FormGroup
                  role="group"
                  label="Eventos"
                  isRequired
                  isStack
                  fieldId={`${formId}-events`}
                >
                  {EVENT_OPTIONS.map((event) => (
                    <Checkbox
                      key={event}
                      id={`${formId}-${event}`}
                      label={WEBHOOK_EVENT_LABELS[event]}
                      description={event === "*" ? undefined : event}
                      isChecked={picked.includes(event)}
                      isDisabled={event !== "*" && picked.includes("*")}
                      onChange={(_event, checked) => toggle(event, checked)}
                    />
                  ))}
                  {fieldState.error ? (
                    <FormHelperText>
                      <HelperText>
                        <HelperTextItem variant="error">
                          {fieldState.error.message}
                        </HelperTextItem>
                      </HelperText>
                    </FormHelperText>
                  ) : null}
                </FormGroup>
              );
            }}
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
          Crear webhook
        </Button>
        <Button variant="link" onClick={onClose}>
          Cancelar
        </Button>
      </ModalFooter>
    </>
  );
};
