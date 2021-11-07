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
)<void, User, AxiosError | string>();

export const fetchCurrentUser = () => {
  return (dispatch: any) => {
    dispatch(fetchRequest());

    return whoAmI()
      .then((response) => {
        // For dev mode purposes
        if (response.headers["content-type"].indexOf("text/html") !== -1) {
          dispatch(fetchFailure("Invalid response"));
        } else {
          dispatch(fetchSuccess(response.data));
        }
      })
      .catch((error) => {
        dispatch(fetchFailure(error));
      });
  };
};
