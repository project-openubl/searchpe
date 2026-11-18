import type React from "react";

import {
  Alert,
  DescriptionList,
  DescriptionListDescription,
  DescriptionListGroup,
  DescriptionListTerm,
  ExpandableSection,
  Stack,
  StackItem,
} from "@patternfly/react-core";
import { Table, Tbody, Td, Th, Thead, Tr } from "@patternfly/react-table";

import type { ISireReconciliation } from "@app/queries/xhandler/sire";

interface ISireReconciliationProps {
  reconciliation: ISireReconciliation;
}

const formatCount = (value: number | undefined): string =>
  (value ?? 0).toLocaleString("es-PE");

const formatAmount = (value: string | undefined): string => {
  const amount = Number(value);
  return value && Number.isFinite(amount)
    ? amount.toLocaleString("es-PE", {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      })
    : (value ?? "-");
};

interface IKeyListProps {
  title: string;
  ariaLabel: string;
  keys: string[];
}

/** A list of comprobantes (`tipo-serie-número`, with the supplier's RUC for purchases). */
const KeyList: React.FC<IKeyListProps> = ({ title, ariaLabel, keys }) =>
  keys.length === 0 ? null : (
    <ExpandableSection toggleText={`${title} (${formatCount(keys.length)})`}>
      <Table aria-label={ariaLabel} variant="compact">
        <Thead>
          <Tr>
            <Th>Comprobante</Th>
          </Tr>
        </Thead>
        <Tbody>
          {keys.map((key) => (
            <Tr key={key}>
              <Td dataLabel="Comprobante">{key}</Td>
            </Tr>
          ))}
        </Tbody>
      </Table>
    </ExpandableSection>
  );

/**
 * SUNAT's proposal against the stored documents (DESIGN.md §11.7): what SUNAT lacks, what it has
 * that isn't stored, and different totals. The server lists at most 200 of each.
 */
export const SireReconciliation: React.FC<ISireReconciliationProps> = ({
  reconciliation,
}) => {
  const missingInSunat = reconciliation.missing_in_sunat ?? [];
  const missingLocally = reconciliation.missing_locally ?? [];
  const differing = reconciliation.differing ?? [];

  return (
    <Stack hasGutter>
      <StackItem>
        {reconciliation.matches ? (
          <Alert
            variant="success"
            isInline
            title="La propuesta de SUNAT coincide con los comprobantes registrados"
          />
        ) : (
          <Alert
            variant="warning"
            isInline
            title="La propuesta de SUNAT no coincide con los comprobantes registrados"
          >
            El reemplazo se arma solo con los comprobantes registrados: lo que
            SUNAT tiene y la plataforma no, se quitará del registro.
          </Alert>
        )}
      </StackItem>
      <StackItem>
        <DescriptionList
          aria-label="Resumen de la conciliación"
          columnModifier={{ md: "3Col" }}
          isCompact
        >
          <DescriptionListGroup>
            <DescriptionListTerm>Comprobantes en SUNAT</DescriptionListTerm>
            <DescriptionListDescription>
              {formatCount(reconciliation.sunat_count)}
            </DescriptionListDescription>
          </DescriptionListGroup>
          <DescriptionListGroup>
            <DescriptionListTerm>Comprobantes registrados</DescriptionListTerm>
            <DescriptionListDescription>
              {formatCount(reconciliation.local_count)}
            </DescriptionListDescription>
          </DescriptionListGroup>
          <DescriptionListGroup>
            <DescriptionListTerm>Con totales distintos</DescriptionListTerm>
            <DescriptionListDescription>
              {formatCount(differing.length)}
            </DescriptionListDescription>
          </DescriptionListGroup>
        </DescriptionList>
      </StackItem>
      <StackItem>
        <KeyList
          title="Registrados que SUNAT no tiene"
          ariaLabel="Comprobantes que SUNAT no tiene"
          keys={missingInSunat}
        />
      </StackItem>
      <StackItem>
        <KeyList
          title="En SUNAT y no registrados en la plataforma"
          ariaLabel="Comprobantes no registrados en la plataforma"
          keys={missingLocally}
        />
      </StackItem>
      {differing.length > 0 ? (
        <StackItem>
          <ExpandableSection
            toggleText={`Con totales distintos (${formatCount(differing.length)})`}
          >
            <Table
              aria-label="Comprobantes con totales distintos"
              variant="compact"
            >
              <Thead>
                <Tr>
                  <Th>Comprobante</Th>
                  <Th>Total en SUNAT</Th>
                  <Th>Total registrado</Th>
                </Tr>
              </Thead>
              <Tbody>
                {differing.map((row) => (
                  <Tr key={row.key}>
                    <Td dataLabel="Comprobante">{row.key ?? "-"}</Td>
                    <Td dataLabel="Total en SUNAT">
                      {formatAmount(row.sunat_total)}
                    </Td>
                    <Td dataLabel="Total registrado">
                      {formatAmount(row.local_total)}
                    </Td>
                  </Tr>
                ))}
              </Tbody>
            </Table>
          </ExpandableSection>
        </StackItem>
      ) : null}
    </Stack>
  );
};
