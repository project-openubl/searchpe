import type React from "react";
import { useId, useState } from "react";

import {
  Button,
  type ButtonProps,
  Form,
  FormGroup,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
  type ModalHeaderProps,
  Stack,
  StackItem,
  TextInput,
} from "@patternfly/react-core";

interface IConfirmActionModalProps {
  isOpen: boolean;
  title: string;
  body: React.ReactNode;
  /** Shows a loading spinner on the confirm button while the action runs. */
  isConfirming?: boolean;
  confirmLabel?: string;
  cancelLabel?: string;
  /** Confirm button variant — `danger` for destructive actions (default). */
  confirmVariant?: ButtonProps["variant"];
  /** Icon beside the title, e.g. `warning` for actions that are hard to undo. */
  titleIconVariant?: ModalHeaderProps["titleIconVariant"];
  /** Text the user must type to enable the confirm button. */
  confirmText?: string;
  onConfirm: () => void;
  onCancel: () => void;
}

/**
 * Confirmation dialog for consequential actions. Purely presentational — the
 * caller owns the mutation and its toasts.
 */
export const ConfirmActionModal: React.FC<IConfirmActionModalProps> = ({
  isOpen,
  onCancel,
  ...props
}) => (
  <Modal isOpen={isOpen} onClose={onCancel} variant="small">
    <ConfirmActionModalContent onCancel={onCancel} {...props} />
  </Modal>
);

/** Mounted only while open, so the typed confirmation resets on every open. */
const ConfirmActionModalContent: React.FC<
  Omit<IConfirmActionModalProps, "isOpen">
> = ({
  title,
  body,
  isConfirming,
  confirmLabel = "Eliminar",
  cancelLabel = "Cancelar",
  confirmVariant = "danger",
  titleIconVariant,
  confirmText,
  onConfirm,
  onCancel,
}) => {
  const inputId = useId();
  const [typed, setTyped] = useState("");
  const isConfirmDisabled = !!confirmText && typed !== confirmText;

  return (
    <>
      <ModalHeader title={title} titleIconVariant={titleIconVariant} />
      <ModalBody>
        {confirmText ? (
          <Stack hasGutter>
            <StackItem>{body}</StackItem>
            <StackItem>
              <Form onSubmit={(event) => event.preventDefault()}>
                <FormGroup
                  label={
                    <>
                      Escribe <strong>{confirmText}</strong> para confirmar
                    </>
                  }
                  fieldId={inputId}
                >
                  <TextInput
                    id={inputId}
                    value={typed}
                    onChange={(_event, value) => setTyped(value)}
                    autoComplete="off"
                  />
                </FormGroup>
              </Form>
            </StackItem>
          </Stack>
        ) : (
          body
        )}
      </ModalBody>
      <ModalFooter>
        <Button
          variant={confirmVariant}
          onClick={onConfirm}
          isLoading={isConfirming}
          isDisabled={isConfirmDisabled}
        >
          {confirmLabel}
        </Button>
        <Button variant="link" onClick={onCancel}>
          {cancelLabel}
        </Button>
      </ModalFooter>
    </>
  );
};
