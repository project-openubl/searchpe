import type React from "react";
import { useId } from "react";

import {
  Alert,
  Button,
  ClipboardCopy,
  DescriptionList,
  DescriptionListDescription,
  DescriptionListGroup,
  DescriptionListTerm,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
  Stack,
  StackItem,
} from "@patternfly/react-core";

import type { NubefactToken } from "@app/client/xhandler";

interface IAdapterTokenCreatedModalProps {
  token: NubefactToken;
  onClose: () => void;
}

/** Shows a new adapter token the only time the service returns it. */
export const AdapterTokenCreatedModal: React.FC<
  IAdapterTokenCreatedModalProps
> = ({ token, onClose }) => {
  const titleId = useId();
  return (
    <Modal isOpen variant="medium" onClose={onClose} aria-labelledby={titleId}>
      <ModalHeader title="Token creado" labelId={titleId} />
      <ModalBody>
        <Stack hasGutter>
          <StackItem>
            <Alert
              variant="warning"
              isInline
              title="Copia el token ahora: no se volverá a mostrar."
            >
              Configúralo en tu ERP como el token de Nubefact, con la ruta de
              abajo como URL.
            </Alert>
          </StackItem>
          <StackItem>
            <DescriptionList aria-label="Token creado">
              <DescriptionListGroup>
                <DescriptionListTerm>Token</DescriptionListTerm>
                <DescriptionListDescription>
                  <ClipboardCopy
                    isReadOnly
                    hoverTip="Copiar"
                    clickTip="Copiado"
                    textAriaLabel="Token"
                    toggleAriaLabel="Mostrar el token"
                  >
                    {token.token ?? ""}
                  </ClipboardCopy>
                </DescriptionListDescription>
              </DescriptionListGroup>
              <DescriptionListGroup>
                <DescriptionListTerm>Ruta</DescriptionListTerm>
                <DescriptionListDescription>
                  <ClipboardCopy
                    isReadOnly
                    hoverTip="Copiar"
                    clickTip="Copiado"
                    textAriaLabel="Ruta"
                    toggleAriaLabel="Mostrar la ruta"
                  >
                    {token.url ?? ""}
                  </ClipboardCopy>
                </DescriptionListDescription>
              </DescriptionListGroup>
            </DescriptionList>
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
