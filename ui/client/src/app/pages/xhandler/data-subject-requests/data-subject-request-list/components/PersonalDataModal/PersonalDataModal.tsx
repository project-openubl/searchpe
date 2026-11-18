import type React from "react";
import { useId } from "react";

import dayjs from "dayjs";

import {
  Alert,
  Bullseye,
  Button,
  Content,
  ContentVariants,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
  PaginationVariant,
  Spinner,
} from "@patternfly/react-core";
import { Table, Tbody, Td, Th, Thead, Tr } from "@patternfly/react-table";

import { ApiErrorText } from "@app/components/ApiErrorText";
import { DocumentStatusLabel } from "@app/components/DocumentStatusLabel";
import { ListPagination } from "@app/components/ListPagination";
import { RENDER_DATE_FORMAT } from "@app/constants";
import { usePersistentState } from "@app/hooks/usePersistentState";
import {
  type IDataSubjectRequestRow,
  personalDataDocuments,
  usePersonalDataQuery,
} from "@app/queries/xhandler/data-subject-requests";
import { getMutationErrorMessage } from "@app/utils/apiError";
import {
  formatTotal,
  tipoComprobanteLabel,
} from "@app/utils/documentPresentation";
import { PAGINATION_STATE, toPageParams } from "@app/utils/listState";

interface IPersonalDataModalProps {
  request: IDataSubjectRequestRow;
  onClose: () => void;
}

/** What the platform holds about the request's person: the documents issued to them (an audited read). */
export const PersonalDataModal: React.FC<IPersonalDataModalProps> = ({
  request,
  onClose,
}) => {
  const titleId = useId();
  const { data, isLoading, error } = usePersonalDataQuery({
    requestId: request.id,
  });
  // A list in a dialog keeps its page on screen only (ADR 0008).
  const [pagination, setPagination] = usePersistentState({
    ...PAGINATION_STATE,
    storage: "state",
  });
  const documents = data ? personalDataDocuments(data) : [];
  const { limit, offset } = toPageParams(pagination);
  const pageDocuments = documents.slice(offset, offset + limit);

  const body = () => {
    if (isLoading) {
      return (
        <Bullseye>
          <Spinner aria-label="Cargando datos personales" />
        </Bullseye>
      );
    }
    if (error) {
      const message = getMutationErrorMessage({
        error,
        fallback: "No se pudieron cargar los datos personales",
      });
      return (
        <Alert variant="danger" isInline title={message.title}>
          <ApiErrorText error={message} />
        </Alert>
      );
    }
    if (documents.length === 0) {
      return (
        <Content component={ContentVariants.p}>
          No hay comprobantes emitidos a esta persona.
        </Content>
      );
    }
    return (
      <>
        <Table
          aria-label="Comprobantes emitidos a la persona"
          variant="compact"
        >
          <Thead>
            <Tr>
              <Th>Emisor</Th>
              <Th>Comprobante</Th>
              <Th>Emisión</Th>
              <Th>Nombre</Th>
              <Th>Total</Th>
              <Th>Estado</Th>
            </Tr>
          </Thead>
          <Tbody>
            {pageDocuments.map((document) => (
              <Tr key={document.id}>
                <Td dataLabel="Emisor">{document.issuerRuc}</Td>
                <Td dataLabel="Comprobante">
                  {tipoComprobanteLabel(document.tipoComprobante)}{" "}
                  {document.serieNumero}
                </Td>
                <Td dataLabel="Emisión">
                  {document.fechaEmision
                    ? dayjs(document.fechaEmision).format(RENDER_DATE_FORMAT)
                    : "-"}
                </Td>
                <Td dataLabel="Nombre">{document.nombre}</Td>
                <Td dataLabel="Total">
                  {formatTotal({
                    total: document.total,
                    moneda: document.moneda,
                  })}
                </Td>
                <Td dataLabel="Estado">
                  <DocumentStatusLabel status={document.status} />
                </Td>
              </Tr>
            ))}
          </Tbody>
        </Table>
        <ListPagination
          variant={PaginationVariant.bottom}
          itemCount={documents.length}
          pagination={pagination}
          onPaginationChange={setPagination}
        />
      </>
    );
  };

  return (
    <Modal isOpen variant="large" onClose={onClose} aria-labelledby={titleId}>
      <ModalHeader
        title={`Datos personales de ${request.subjectName || request.subjectDocNumber}`}
        labelId={titleId}
        description="Comprobantes emitidos a la persona: estado e importes, sin archivos. Esta consulta queda registrada."
      />
      <ModalBody>{body()}</ModalBody>
      <ModalFooter>
        <Button variant="link" onClick={onClose}>
          Cerrar
        </Button>
      </ModalFooter>
    </Modal>
  );
};
