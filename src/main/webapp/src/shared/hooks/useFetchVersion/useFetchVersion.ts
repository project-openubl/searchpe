import { useCallback, useReducer } from "react";
import { AxiosError } from "axios";
import { ActionType, createAsyncAction, getType } from "typesafe-actions";

import { getVersion } from "api/rest";
import { Version } from "api/models";

export const {
  request: fetchRequest,
  success: fetchSuccess,
  failure: fetchFailure,
} = createAsyncAction(
  "useFetchVersion/fetch/request",
  "useFetchVersion/fetch/success",
  "useFetchVersion/fetch/failure"
)<void, Version, AxiosError>();

type State = Readonly<{
  isFetching: boolean;
  version?: Version;
  fetchError?: AxiosError;
}>;

const defaultState: State = {
  isFetching: false,
  version: undefined,
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
        version: action.payload,
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
  version?: Version;
  isFetching: boolean;
  fetchError?: AxiosError;
  fetchVersion: (name: number) => void;
}

export const useFetchVersion = (defaultIsFetching: boolean = false): IState => {
  const [state, dispatch] = useReducer(reducer, defaultIsFetching, initReducer);

  const fetchVersion = useCallback((name: number) => {
    dispatch(fetchRequest());

    getVersion(name)
      .then(({ data }) => {
        dispatch(fetchSuccess(data));
      })
      .catch((error: AxiosError) => {
        dispatch(fetchFailure(error));
      });
  }, []);

  return {
    version: state.version,
    isFetching: state.isFetching,
    fetchError: state.fetchError,
    fetchVersion,
  };
};

export default useFetchVersion;
