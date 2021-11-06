import { User } from "api/models";
import { AxiosError } from "axios";
import { ActionType, getType } from "typesafe-actions";
import { fetchRequest, fetchSuccess, fetchFailure } from "./actions";

export const stateKey = "currentUser";

export type CurrentUserState = Readonly<{
  user?: User;
  isFetching: boolean;
  fetchError?: AxiosError;
}>;

export const defaultState: CurrentUserState = {
  user: undefined,
  isFetching: false,
  fetchError: undefined,
};

export type CurrentUserAction = ActionType<
  typeof fetchRequest | typeof fetchSuccess | typeof fetchFailure
>;

export const reducer = (
  state: CurrentUserState = defaultState,
  action: CurrentUserAction
): CurrentUserState => {
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
        user: action.payload,
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
