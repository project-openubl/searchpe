import { User } from "api/models";
import { whoAmI } from "api/rest";
import { AxiosError } from "axios";
import { createAsyncAction } from "typesafe-actions";

export const {
  request: fetchRequest,
  success: fetchSuccess,
  failure: fetchFailure,
} = createAsyncAction(
  "currentUser/fetch/request",
  "currentUser/fetch/success",
  "currentUser/fetch/failure"
)<void, User, AxiosError>();

export const fetchCurrentUser = () => {
  return (dispatch: any) => {
    dispatch(fetchRequest());

    return whoAmI()
      .then(({ data }) => {
        dispatch(fetchSuccess(data));
      })
      .catch((error) => {
        dispatch(fetchFailure(error));
      });
  };
};
