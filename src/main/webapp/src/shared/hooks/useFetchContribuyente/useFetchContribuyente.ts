import { useCallback, useReducer } from "react";
import { AxiosError } from "axios";
import { ActionType, createAsyncAction, getType } from "typesafe-actions";

import { getContribuyente } from "api/rest";
import { Contribuyente } from "api/models";

export const {
  request: fetchRequest,
  success: fetchSuccess,
  failure: fetchFailure,
} = createAsyncAction(
  "useFetchContribuyente/fetch/request",
  "useFetchContribuyente/fetch/success",
  "useFetchContribuyente/fetch/failure"
)<void, Contribuyente, AxiosError>();

type State = Readonly<{
  isFetching: boolean;
  contribuyente?: Contribuyente;
  fetchError?: AxiosError;
}>;

const defaultState: State = {
  isFetching: false,
  contribuyente: undefined,
  fetchError: undefined,
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
        contribuyente: action.payload,
      };
    case getType(fetchFailure):
      return {
        ...state,
        isFetching: false,
        fetchError: action.payload,
      };
    default:
      return state;
  }
};

export interface IState {
  contribuyente?: Contribuyente;
  isFetching: boolean;
  fetchError?: AxiosError;
  fetchContribuyente: (name: string) => void;
}

export const useFetchContribuyente = (
  defaultIsFetching: boolean = false
): IState => {
  const [state, dispatch] = useReducer(reducer, defaultIsFetching, initReducer);

  const fetchContribuyente = useCallback((name: string) => {
    dispatch(fetchRequest());

    getContribuyente(name)
      .then(({ data }) => {
        dispatch(fetchSuccess(data));
      })
      .catch((error: AxiosError) => {
        dispatch(fetchFailure(error));
      });
  }, []);

  return {
    contribuyente: state.contribuyente,
    isFetching: state.isFetching,
    fetchError: state.fetchError,
    fetchContribuyente,
  };
};

export default useFetchContribuyente;
