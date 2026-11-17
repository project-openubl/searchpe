import React from "react";
import {
  Button,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
  ModalVariant,
} from "@patternfly/react-core";

import { Contribuyente } from "api/models";
import { ContribuyenteDetails } from "shared/components";

export interface DetailsModalProps {
  value?: Contribuyente;
  onClose: () => void;
}

export const DetailsModal: React.FC<DetailsModalProps> = ({
  value,
  onClose,
}) => {
  return (
    <Modal
      variant={ModalVariant.medium}
      isOpen={!!value}
      onClose={onClose}
      aria-label="details-modal"
      aria-labelledby="details-modal-title"
    >
      <ModalHeader title="Detalle" labelId="details-modal-title" />
      <ModalBody>{value && <ContribuyenteDetails value={value} />}</ModalBody>
      <ModalFooter>
        <Button key="close" variant="primary" onClick={onClose}>
          Cerrar
        </Button>
      </ModalFooter>
    </Modal>
  );
};
