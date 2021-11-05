import React, { useEffect } from "react";
import { RouteComponentProps } from "react-router-dom";
import Moment from "react-moment";

import {
  Button,
  ButtonVariant,
  EmptyState,
  EmptyStateBody,
  EmptyStateIcon,
  EmptyStateVariant,
  PageSection,
  Title,
  ToolbarGroup,
  ToolbarItem,
} from "@patternfly/react-core";
import {
  IAction,
  ICell,
  IExtraColumnData,
  IExtraData,
  IRowData,
  ISeparator,
  sortable,
  SortByDirection,
} from "@patternfly/react-table";
import { AddCircleOIcon } from "@patternfly/react-icons";

import {
  AppPlaceholder,
  AppTableWithControls,
  ConditionalRender,
  SimplePageSection,
  VersionStatusIcon,
} from "shared/components";
import {
  useDeleteVersion,
  useFetchVersions,
  useTableControls,
  useTableControlsOffline,
} from "shared/hooks";

import { useDispatch } from "react-redux";
import { deleteDialogActions } from "store/deleteDialog";
import { alertActions } from "store/alert";

import { createVersion } from "api/rest";
import { Version } from "api/models";
import { formatNumber, getAxiosErrorMessage } from "utils/modelUtils";

const columns: ICell[] = [
  { title: "Id", transforms: [sortable] },
  { title: "Created", transforms: [sortable] },
  { title: "Records" },
  { title: "Status" },
];

const columnIndexToField = (
  _: React.MouseEvent,
  index: number,
  direction: SortByDirection,
  extraData: IExtraColumnData
) => {
  switch (index) {
    case 0:
      return "id";
    case 1:
      return "createdAt";
    default:
      throw new Error("Invalid column index=" + index);
  }
};

const VERSION_FIELD = "version";

const getRow = (rowData: IRowData): Version => {
  return rowData[VERSION_FIELD];
};

const itemsToRow = (items: Version[]) => {
  return items.map((item) => ({
    [VERSION_FIELD]: item,
    cells: [
      {
        title: `#${item.id}`,
      },
      {
        title: <Moment fromNow>{item.createdAt}</Moment>,
      },
      {
        title: formatNumber(item.records, 0),
      },
      {
        title: <VersionStatusIcon value={item.status} />,
      },
    ],
  }));
};

export const compareByColumnIndex = (
  a: Version,
  b: Version,
  columnIndex?: number
) => {
  switch (columnIndex) {
    case 0: // id
      return a.id - b.id;
    case 1: // createdAt
      return new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
    default:
      return 0;
  }
};

export const filterByText = (filterText: string, item: Version) => {
  return (
    item.id.toString().toLowerCase().indexOf(filterText.toLowerCase()) !== -1
  );
};

export interface VersionListProps extends RouteComponentProps {}

export const VersionList: React.FC<VersionListProps> = () => {
  const dispatch = useDispatch();

  const { deleteVersion } = useDeleteVersion();

  const {
    versions,
    isFetching,
    fetchError,
    fetchVersions,
  } = useFetchVersions();
  const {
    filterText,
    paginationQuery,
    sortBy,
    handleFilterTextChange,
    handlePaginationChange,
    handleSortChange,
  } = useTableControls({ columnToField: columnIndexToField });
  const { pageItems, filteredItems } = useTableControlsOffline({
    items: versions || [],
    filterText: filterText,
    paginationQuery: paginationQuery,
    sortBy: sortBy,
    compareByColumnIndex: compareByColumnIndex,
    filterByText: filterByText,
  });

  useEffect(() => {
    fetchVersions({ watch: true });
  }, [fetchVersions]);

  const actionResolver = (rowData: IRowData): (IAction | ISeparator)[] => {
    const row: Version = getRow(rowData);

    const actions: (IAction | ISeparator)[] = [];

    if (row.status !== "COMPLETED" && row.status !== "ERROR") {
      // actions.push({
      //   title: "Cancel",
      //   onClick: (_, rowIndex: number, rowData: IRowData) => {
      //   },
      // });
    } else {
      actions.push({
        title: "Delete",
        onClick: (
          event: React.MouseEvent,
          rowIndex: number,
          rowData: IRowData,
          extraData: IExtraData
        ) => {
          const row: Version = getRow(rowData);
          dispatch(
            deleteDialogActions.openModal({
              name: `version #${row.id}`,
              type: "version",
              onDelete: () => {
                dispatch(deleteDialogActions.processing());
                deleteVersion(
                  row,
                  () => {
                    dispatch(deleteDialogActions.closeModal());
                    fetchVersions({ watch: true });
                  },
                  (error) => {
                    dispatch(deleteDialogActions.closeModal());
                    dispatch(
                      alertActions.addAlert(
                        "danger",
                        "Error",
                        getAxiosErrorMessage(error)
                      )
                    );
                  }
                );
              },
            })
          );
        },
      });
    }

    return actions;
  };

  const areActionsDisabled = (): boolean => {
    return false;
  };

  const handleNewVersion = () => {
    createVersion()
      .then(() => {
        fetchVersions({ watch: true });
      })
      .catch((error) => {
        dispatch(
          alertActions.addAlert("danger", "Error", getAxiosErrorMessage(error))
        );
      });
  };

  return (
    <>
      <ConditionalRender
        when={isFetching && !(versions || fetchError)}
        then={<AppPlaceholder />}
      >
        <SimplePageSection
          title="Versions"
          description="Versions store a specific point in time data."
        />
        <PageSection>
          <AppTableWithControls
            count={filteredItems.length}
            items={pageItems}
            itemsToRow={itemsToRow}
            pagination={paginationQuery}
            sortBy={sortBy}
            handleFilterTextChange={handleFilterTextChange}
            handlePaginationChange={handlePaginationChange}
            handleSortChange={handleSortChange}
            columns={columns}
            actionResolver={actionResolver}
            areActionsDisabled={areActionsDisabled}
            isLoading={isFetching}
            loadingVariant="none"
            fetchError={fetchError}
            toolbar={
              <ToolbarGroup variant="button-group">
                <ToolbarItem>
                  <Button
                    type="button"
                    aria-label="new-version"
                    variant={ButtonVariant.primary}
                    onClick={handleNewVersion}
                  >
                    New version
                  </Button>
                </ToolbarItem>
              </ToolbarGroup>
            }
            filtersApplied={filterText.trim().length > 0}
            noDataState={
              <EmptyState variant={EmptyStateVariant.small}>
                <EmptyStateIcon icon={AddCircleOIcon} />
                <Title headingLevel="h2" size="lg">
                  No versions available
                </Title>
                <EmptyStateBody>
                  Add a version by clicking on <strong>New version</strong>.
                </EmptyStateBody>
              </EmptyState>
            }
          />
        </PageSection>
      </ConditionalRender>
    </>
  );
};
