import type React from "react";

import dayjs from "dayjs";

import {
  Alert,
  Card,
  CardBody,
  CardTitle,
  Content,
  ContentVariants,
  DescriptionList,
  DescriptionListDescription,
  DescriptionListGroup,
  DescriptionListTerm,
  Gallery,
  Label,
  PageSection,
  Spinner,
  Stack,
  StackItem,
} from "@patternfly/react-core";
import { DataView, DataViewTable } from "@patternfly/react-data-view";

import type { Status } from "@app/client/xhandler";
import { dataViewBodyStates } from "@app/components/dataViewStates";
import { DocumentTitle } from "@app/components/DocumentTitle";
import { TableEmptyState } from "@app/components/TableEmptyState";
import { RENDER_DATETIME_FORMAT } from "@app/constants";
import { useXhandlerStatusQuery } from "@app/queries/xhandler/status";
import { getMutationErrorMessage } from "@app/utils/apiError";
import type { DataViewTh } from "@app/utils/dataViewTypes";

import {
  componentHealth,
  PRIORITY_CLASS_LABELS,
  sunatHealth,
} from "./serviceHealth";

interface IHealthLabelProps {
  isHealthy: boolean | undefined;
}

const HealthLabel: React.FC<IHealthLabelProps> = ({ isHealthy }) =>
  isHealthy === undefined ? (
    <Label isCompact>Desconocido</Label>
  ) : isHealthy ? (
    <Label color="green" isCompact>
      Operativo
    </Label>
  ) : (
    <Label color="red" isCompact>
      Con fallas
    </Label>
  );

interface IStatusBodyProps {
  status: Status;
}

const StatusBody: React.FC<IStatusBodyProps> = ({ status }) => {
  const workerColumns: DataViewTh[] = [
    { cell: "Rol", props: { width: 70 } },
    { cell: "En línea", props: { width: 30 } },
  ];
  const workerRows = (status.workers ?? []).map((worker) => ({
    id: worker.role ?? "",
    row: [
      { cell: worker.role ?? "-" },
      { cell: (worker.online ?? 0).toLocaleString("es-PE") },
    ],
  }));
  const workers = dataViewBodyStates({
    columnCount: workerColumns.length,
    isEmpty: workerRows.length === 0,
    emptyState: <TableEmptyState title="No hay procesos en línea" />,
  });

  const queueColumns: DataViewTh[] = [
    { cell: "Prioridad", props: { width: 25 } },
    { cell: "En espera", props: { width: 15 } },
    { cell: "Listos", props: { width: 15 } },
    { cell: "En ejecución", props: { width: 15 } },
    { cell: "Más antiguo listo", props: { width: 30 } },
  ];
  const queueRows = (status.queues ?? []).map((queue) => ({
    id: String(queue.priority_class ?? ""),
    row: [
      {
        cell:
          PRIORITY_CLASS_LABELS[queue.priority_class ?? -1] ??
          String(queue.priority_class ?? "-"),
      },
      { cell: (queue.waiting ?? 0).toLocaleString("es-PE") },
      { cell: (queue.unblocked ?? 0).toLocaleString("es-PE") },
      { cell: (queue.running ?? 0).toLocaleString("es-PE") },
      {
        cell: queue.oldest_unblocked
          ? dayjs(queue.oldest_unblocked).format(RENDER_DATETIME_FORMAT)
          : "-",
      },
    ],
  }));
  const queues = dataViewBodyStates({
    columnCount: queueColumns.length,
    isEmpty: queueRows.length === 0,
    emptyState: <TableEmptyState title="No hay trabajos en cola" />,
  });

  return (
    <Stack hasGutter>
      <StackItem>
        <Gallery hasGutter minWidths={{ default: "100%", md: "220px" }}>
          {componentHealth(status).map(({ label, isHealthy }) => (
            <Card key={label} isCompact>
              <CardTitle component="h2">{label}</CardTitle>
              <CardBody>
                <HealthLabel isHealthy={isHealthy} />
              </CardBody>
            </Card>
          ))}
        </Gallery>
      </StackItem>
      <StackItem>
        <Card>
          <CardTitle component="h2">SUNAT</CardTitle>
          <CardBody>
            <Stack hasGutter>
              <StackItem>
                <Content component={ContentVariants.small}>
                  Disponibilidad de los servicios web de SUNAT según los envíos
                  recientes. Mientras un servicio está caído, los comprobantes
                  esperan y se envían cuando vuelve.
                </Content>
              </StackItem>
              <StackItem>
                <Gallery hasGutter minWidths={{ default: "100%", md: "260px" }}>
                  {sunatHealth(status).map((env) => (
                    <Card key={env.environment} isCompact isPlain>
                      <CardTitle component="h3">{env.label}</CardTitle>
                      <CardBody>
                        {env.isAvailable ? (
                          <Label color="green" isCompact>
                            Disponible
                          </Label>
                        ) : (
                          <Label color="red" isCompact>
                            No disponible
                          </Label>
                        )}
                        {env.services.length > 0 ? (
                          <DescriptionList
                            isCompact
                            isHorizontal
                            aria-label={`Servicios de SUNAT en ${env.label}`}
                          >
                            {env.services.map((service) => (
                              <DescriptionListGroup key={service.label}>
                                <DescriptionListTerm>
                                  {service.label}
                                </DescriptionListTerm>
                                <DescriptionListDescription>
                                  <Label
                                    color={service.isUp ? "green" : "orange"}
                                    isCompact
                                  >
                                    {service.state}
                                  </Label>
                                  {!service.isUp && service.until
                                    ? ` hasta ${dayjs(service.until).format(RENDER_DATETIME_FORMAT)}`
                                    : null}
                                </DescriptionListDescription>
                              </DescriptionListGroup>
                            ))}
                          </DescriptionList>
                        ) : null}
                      </CardBody>
                    </Card>
                  ))}
                </Gallery>
              </StackItem>
            </Stack>
          </CardBody>
        </Card>
      </StackItem>
      <StackItem>
        <Card>
          <CardTitle component="h2">Versiones</CardTitle>
          <CardBody>
            <DescriptionList
              aria-label="Versiones del servicio"
              columnModifier={{ md: "3Col" }}
            >
              <DescriptionListGroup>
                <DescriptionListTerm>xhandler-service</DescriptionListTerm>
                <DescriptionListDescription>
                  {status.versions?.service ?? "-"}
                </DescriptionListDescription>
              </DescriptionListGroup>
              <DescriptionListGroup>
                <DescriptionListTerm>Motor</DescriptionListTerm>
                <DescriptionListDescription>
                  {status.versions?.engine ?? "-"}
                </DescriptionListDescription>
              </DescriptionListGroup>
              <DescriptionListGroup>
                <DescriptionListTerm>xhandler-rust</DescriptionListTerm>
                <DescriptionListDescription>
                  {status.versions?.xhandler_rust ?? "-"}
                </DescriptionListDescription>
              </DescriptionListGroup>
            </DescriptionList>
          </CardBody>
        </Card>
      </StackItem>
      <StackItem>
        <Card>
          <CardTitle component="h2">Procesos</CardTitle>
          <CardBody>
            <DataView activeState={workers.activeState}>
              <DataViewTable
                aria-label="Tabla de procesos"
                columns={workerColumns}
                rows={workerRows}
                bodyStates={workers.bodyStates}
              />
            </DataView>
          </CardBody>
        </Card>
      </StackItem>
      <StackItem>
        <Card>
          <CardTitle component="h2">Colas de trabajos</CardTitle>
          <CardBody>
            <DataView activeState={queues.activeState}>
              <DataViewTable
                aria-label="Tabla de colas"
                columns={queueColumns}
                rows={queueRows}
                bodyStates={queues.bodyStates}
              />
            </DataView>
          </CardBody>
        </Card>
      </StackItem>
    </Stack>
  );
};

/** xhandler-service's public status (`GET /api/v1/status`), refreshed every minute. */
export const ServiceStatus: React.FC = () => {
  const { data, isLoading, error, dataUpdatedAt } = useXhandlerStatusQuery();

  const body = (() => {
    if (isLoading) return <Spinner aria-label="Cargando estado del servicio" />;
    if (error || !data) {
      const message = getMutationErrorMessage({
        error,
        fallback: "No se pudo consultar el estado del servicio",
      });
      return (
        <Alert variant="danger" isInline title={message.title}>
          {message.description}
        </Alert>
      );
    }
    return <StatusBody status={data} />;
  })();

  return (
    <>
      <DocumentTitle title="Estado del servicio" />
      <PageSection>
        <Content component={ContentVariants.h1}>Estado del servicio</Content>
        <Content component={ContentVariants.p}>
          {dataUpdatedAt
            ? `Componentes, SUNAT, procesos y colas de comprobantes electrónicos; consultado el ${dayjs(dataUpdatedAt).format(RENDER_DATETIME_FORMAT)}.`
            : "Componentes, SUNAT, procesos y colas de comprobantes electrónicos."}
        </Content>
      </PageSection>
      <PageSection>{body}</PageSection>
    </>
  );
};
