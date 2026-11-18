import type React from "react";
import { useId } from "react";

import {
  Alert,
  Button,
  ClipboardCopy,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
  Stack,
  StackItem,
} from "@patternfly/react-core";

interface IAccessLinkModalProps {
  /** The buyer link just created; a credential, shown only here. */
  accessUrl: string;
  onClose: () => void;
}

/** Shows a new buyer link once, to copy it; it's never put in a toast or a log. */
export const AccessLinkModal: React.FC<IAccessLinkModalProps> = ({
  accessUrl,
  onClose,
}) => {
  const titleId = useId();
  return (
    <Modal isOpen variant="small" onClose={onClose} aria-labelledby={titleId}>
      <ModalHeader
        title="Enlace de acceso para el comprador"
        labelId={titleId}
      />
      <ModalBody>
        <Stack hasGutter>
          <StackItem>
            <Alert
              variant="info"
              isInline
              isPlain
              title="Quien tenga este enlace verá el comprobante sin iniciar sesión."
            />
          </StackItem>
          <StackItem>
            <ClipboardCopy
              isReadOnly
              hoverTip="Copiar"
              clickTip="Copiado"
              textAriaLabel="Enlace de acceso"
              toggleAriaLabel="Mostrar el enlace"
            >
              {accessUrl}
            </ClipboardCopy>
          </StackItem>
        </Stack>
      </ModalBody>
      <ModalFooter>
        <Button variant="primary" onClick={onClose}>
          Listo
        </Button>
      </ModalFooter>
    </Modal>
  );
};
