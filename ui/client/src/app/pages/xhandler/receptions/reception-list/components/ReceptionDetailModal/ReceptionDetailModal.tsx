import type React from "react";
import { useId } from "react";

import dayjs from "dayjs";

import {
  ActionList,
  ActionListItem,
  Button,
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

import type { ReceptionView } from "@app/client/xhandler";
import { RENDER_DATE_FORMAT, RENDER_DATETIME_FORMAT } from "@app/constants";
import {
  hasReceptionFile,
  RECEPTION_FILE_KINDS,
  type ReceptionFileKind,
} from "@app/queries/xhandler/receptions";
import {
  formatTotal,
  tipoComprobanteLabel,
} from "@app/utils/documentPresentation";

import { RECEPTION_FILE_LABELS } from "../../receptionFiles";
import { ReceptionValidityLabel } from "../ReceptionValidityLabel";

interface IReceptionDetailModalProps {
  reception: ReceptionView;
  /** The file being downloaded, if any. */
  downloadingKind: ReceptionFileKind | undefined;
  onDownload: (kind: ReceptionFileKind) => void;
  onClose: () => void;
}

const formatTime = (value: string | undefined): string =>
  value ? dayjs(value).format(RENDER_DATETIME_FORMAT) : "-";

/** A received supplier document from its list row (the API has no read by id). */
export const ReceptionDetailModal: React.FC<IReceptionDetailModalProps> = ({
  reception,
  downloadingKind,
  onDownload,
  onClose,
}) => {
  const titleId = useId();
  const fields: { label: string; value: React.ReactNode }[] = [
    { label: "Tipo", value: tipoComprobanteLabel(reception.tipo_comprobante) },
    { label: "Serie y número", value: reception.serie_numero ?? "-" },
    { label: "RUC del proveedor", value: reception.supplier_ruc ?? "-" },
    {
      label: "Fecha de emisión",
      value: reception.fecha_emision
        ? dayjs(reception.fecha_emision).format(RENDER_DATE_FORMAT)
        : "-",
    },
    {
      label: "Total",
      value: formatTotal({ total: reception.total, moneda: reception.moneda }),
    },
    {
      label: "Validez en SUNAT",
      value: <ReceptionValidityLabel state={reception.validity_state} />,
    },
    { label: "Consultado", value: formatTime(reception.validity_checked_at) },
    { label: "Recibido", value: formatTime(reception.created_at) },
  ];

  return (
    <Modal isOpen variant="medium" onClose={onClose} aria-labelledby={titleId}>
      <ModalHeader
        title={`Recepción ${reception.serie_numero ?? ""}`.trim()}
        labelId={titleId}
      />
      <ModalBody>
        <Stack hasGutter>
          <StackItem>
            <DescriptionList
              aria-label="Datos de la recepción"
              isCompact
              isHorizontal
            >
              {fields.map(({ label, value }) => (
                <DescriptionListGroup key={label}>
                  <DescriptionListTerm>{label}</DescriptionListTerm>
                  <DescriptionListDescription>
                    {value}
                  </DescriptionListDescription>
                </DescriptionListGroup>
              ))}
            </DescriptionList>
          </StackItem>
          <StackItem>
            <ActionList>
              {RECEPTION_FILE_KINDS.filter((kind) =>
                hasReceptionFile({ reception, kind }),
              ).map((kind) => (
                <ActionListItem key={kind}>
                  <Button
                    variant="secondary"
                    isLoading={downloadingKind === kind}
                    onClick={() => onDownload(kind)}
                  >
                    Descargar {RECEPTION_FILE_LABELS[kind]}
                  </Button>
                </ActionListItem>
              ))}
            </ActionList>
          </StackItem>
        </Stack>
      </ModalBody>
      <ModalFooter>
        <Button variant="link" onClick={onClose}>
          Cerrar
        </Button>
      </ModalFooter>
    </Modal>
  );
};
