import { useCallback, useReducer } from "react";
import { ActionType, createAction, getType } from "typesafe-actions";
import {
  IExtraColumnData,
  ISortBy,
  SortByDirection,
} from "@patternfly/react-table";

import { PageQuery, SortByQuery } from "api/models";

const setFilterText = createAction("app-table/filterText/change")<string>();
const setSortBy = createAction("app-table/sortBy/change")<{
  index: number;
  fieldName: string;
  direction: "asc" | "desc";
}>();
const setPagination = createAction("app-table/pagination/change")<PageQuery>();

type State = Readonly<{
  changed: boolean;

  filterText: string;
  paginationQuery: PageQuery;
  sortByQuery?: SortByQuery;
  sortBy?: ISortBy;
}>;

const defaultState: State = {
  changed: false,

  filterText: "",
  paginationQuery: {
    page: 1,
    perPage: 10,
  },
  sortByQuery: undefined,
  sortBy: undefined,
};

type Action = ActionType<
  typeof setFilterText | typeof setSortBy | typeof setPagination
>;

const reducer = (state: State, action: Action): State => {
  switch (action.type) {
    case getType(setFilterText):
      return {
        ...state,
        changed: true,
        filterText: action.payload,
        paginationQuery: { page: 1, perPage: state.paginationQuery.perPage },
      };
    case getType(setPagination):
      return {
        ...state,
        changed: true,
        paginationQuery: { ...action.payload },
      };
    case getType(setSortBy):
      return {
        ...state,
        changed: true,
        sortByQuery: {
          orderBy: action.payload.fieldName,
          orderDirection: action.payload.direction,
        },
        sortBy: {
          index: action.payload.index,
          direction: action.payload.direction,
        },
      };
    default:
      return state;
  }
};

// Hook

interface HookArgs {
  columnToField: (
    _: React.MouseEvent,
    index: number,
    direction: SortByDirection,
    extraData: IExtraColumnData
  ) => string;
}

interface HookState {
  filterText: string;
  paginationQuery: PageQuery;
  sortByQuery?: SortByQuery;
  sortBy?: ISortBy;
  handleFilterTextChange: (filterText: string) => void;
  handlePaginationChange: ({
    page,
    perPage,
  }: {
    page: number;
    perPage: number;
  }) => void;
  handleSortChange: (
    event: React.MouseEvent,
    index: number,
    direction: SortByDirection,
    extraData: IExtraColumnData
  ) => void;
}

export const useTableControls = ({
  columnToField: columnIndexToField,
}: HookArgs): HookState => {
  const [state, dispatch] = useReducer(reducer, defaultState);

  const handleFilterTextChange = useCallback((filterText: string) => {
    dispatch(setFilterText(filterText));
  }, []);

  const handlePaginationChange = useCallback(
    ({ page, perPage }: { page: number; perPage: number }) => {
      dispatch(setPagination({ page, perPage }));
    },
    []
  );

  const handleSortChange = useCallback(
    (
      event: React.MouseEvent,
      index: number,
      direction: SortByDirection,
      extraData: IExtraColumnData
    ) => {
      dispatch(
        setSortBy({
          index: index,
          fieldName: columnIndexToField(event, index, direction, extraData),
          direction: direction,
        })
      );
    },
    [columnIndexToField]
  );

  return {
    filterText: state.filterText,
    paginationQuery: state.paginationQuery,
    sortByQuery: state.sortByQuery,
    sortBy: state.sortBy,
    handleFilterTextChange,
    handlePaginationChange,
    handleSortChange,
  };
};
