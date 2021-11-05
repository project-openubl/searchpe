import React from "react";
import { Bullseye, Spinner, Skeleton } from "@patternfly/react-core";
import {
  Table,
  TableHeader,
  TableBody,
  ICell,
  IRow,
  IActions,
  ISortBy,
  IActionsResolver,
  IAreActionsDisabled,
  OnSort,
} from "@patternfly/react-table";

import { StateNoData } from "./state-no-data";
import { StateNoResults } from "./state-no-results";
import { StateError } from "./state-error";

export interface AppTableProps {
  columns: ICell[];
  rows: IRow[];
  actions?: IActions;
  actionResolver?: IActionsResolver;
  areActionsDisabled?: IAreActionsDisabled;

  isLoading: boolean;
  loadingVariant?: "skeleton" | "spinner" | "none";
  fetchError?: any;

  sortBy?: ISortBy;
  onSort?: OnSort;

  filtersApplied: boolean;
  noDataState?: any;
  noSearchResultsState?: any;
  errorState?: any;
}

const ARIA_LABEL = "App table";

export const AppTable: React.FC<AppTableProps> = ({
  columns,
  rows,
  actions,
  actionResolver,
  areActionsDisabled,
  isLoading,
  fetchError,
  loadingVariant = "skeleton",
  sortBy,
  onSort,
  filtersApplied,
  noDataState,
  noSearchResultsState,
  errorState,
}) => {
  if (isLoading && loadingVariant !== "none") {
    let rows: IRow[] = [];
    if (loadingVariant === "skeleton") {
      rows = [...Array(10)].map(() => {
        return {
          cells: [...Array(columns.length)].map(() => ({
            title: <Skeleton />,
          })),
        };
      });
    } else if (loadingVariant === "spinner") {
      rows = [
        {
          heightAuto: true,
          cells: [
            {
              props: { colSpan: 8 },
              title: (
                <Bullseye>
                  <Spinner size="xl" />
                </Bullseye>
              ),
            },
          ],
        },
      ];
    } else {
      throw new Error("Can not determine the loading state of table");
    }

    return (
      <Table aria-label={ARIA_LABEL} cells={columns} rows={rows}>
        <TableHeader />
        <TableBody />
      </Table>
    );
  }

  if (fetchError) {
    return (
      <>
        <Table aria-label={ARIA_LABEL} cells={columns} rows={[]}>
          <TableHeader />
          <TableBody />
        </Table>
        {errorState ? errorState : <StateError />}
      </>
    );
  }

  if (rows.length === 0) {
    return filtersApplied ? (
      <>
        <Table aria-label={ARIA_LABEL} cells={columns} rows={[]}>
          <TableHeader />
          <TableBody />
        </Table>
        {noSearchResultsState ? noSearchResultsState : <StateNoResults />}
      </>
    ) : (
      <>
        <Table aria-label={ARIA_LABEL} cells={columns} rows={[]}>
          <TableHeader />
          <TableBody />
        </Table>
        {noDataState ? noDataState : <StateNoData />}
      </>
    );
  }

  return (
    <Table
      aria-label={ARIA_LABEL}
      cells={columns}
      rows={rows}
      actions={actions}
      actionResolver={actionResolver}
      areActionsDisabled={areActionsDisabled}
      sortBy={sortBy}
      onSort={onSort}
    >
      <TableHeader />
      <TableBody />
    </Table>
  );
};
