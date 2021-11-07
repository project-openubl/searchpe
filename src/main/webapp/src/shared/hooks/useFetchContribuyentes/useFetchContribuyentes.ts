import { useCallback, useReducer } from "react";
import { AxiosError } from "axios";
import { ActionType, createAsyncAction, getType } from "typesafe-actions";

import { getContribuyentes } from "api/rest";
import {
  PageRepresentation,
  Contribuyente,
  PageQuery,
  SortByQuery,
} from "api/models";

export const {
  request: fetchRequest,
  success: fetchSuccess,
  failure: fetchFailure,
} = createAsyncAction(
  "useFetchContribuyentes/fetch/request",
  "useFetchContribuyentes/fetch/success",
  "useFetchContribuyentes/fetch/failure"
)<void, PageRepresentation<Contribuyente>, AxiosError>();

type State = Readonly<{
  isFetching: boolean;
  contribuyentes?: PageRepresentation<Contribuyente>;
  fetchError?: AxiosError;
  fetchCount: number;
}>;

const defaultState: State = {
  isFetching: false,
  contribuyentes: undefined,
  fetchError: undefined,
  fetchCount: 0,
};

type Action = ActionType<
  typeof fetchRequest | typeof fetchSuccess | typeof fetchFailure
>;

const initReducer = (isFetching: boolean): State => {
  return {
    ...defaultState,
    isFetching,
  };
};

const reducer = (state: State, action: Action): State => {
  switch (action.type) {
    case getType(fetchRequest):
      return {
        ...state,
        isFetching: true,
      };
    case getType(fetchSuccess):
      return {
        ...state,
        isFetching: false,
        fetchError: undefined,
        contribuyentes: action.payload,
        fetchCount: state.fetchCount + 1,
      };
    case getType(fetchFailure):
      return {
        ...state,
        isFetching: false,
        fetchError: action.payload,
        fetchCount: state.fetchCount + 1,
      };
    default:
      return state;
  }
};

export interface IState {
  contribuyentes?: PageRepresentation<Contribuyente>;
  isFetching: boolean;
  fetchError?: AxiosError;
  fetchCount: number;
  fetchContribuyentes: (
    page: PageQuery,
    sortBy?: SortByQuery,
    filterText?: string
  ) => void;
}

export const useFetchContribuyentes = (
  defaultIsFetching: boolean = false
): IState => {
  const [state, dispatch] = useReducer(reducer, defaultIsFetching, initReducer);

  const fetchContribuyentes = useCallback(
    (page: PageQuery, sortBy?: SortByQuery, filterText?: string) => {
      dispatch(fetchRequest());

      getContribuyentes(page, sortBy, filterText)
        .then(({ data }) => {
          dispatch(fetchSuccess(data));
        })
        .catch((error: AxiosError) => {
          dispatch(fetchFailure(error));
        });
    },
    []
  );

  return {
    contribuyentes: state.contribuyentes,
    isFetching: state.isFetching,
    fetchError: state.fetchError,
    fetchCount: state.fetchCount,
    fetchContribuyentes,
  };
};

export default useFetchContribuyentes;
