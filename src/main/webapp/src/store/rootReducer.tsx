import { combineReducers } from "redux";
import { StateType } from "typesafe-actions";

import { notificationsReducer } from "@redhat-cloud-services/frontend-components-notifications/redux";
import { deleteDialogStateKey, deleteDialogReducer } from "./deleteDialog";
import { currentUserStateKey, currentUserReducer } from "./currentUser";

export type RootState = StateType<typeof rootReducer>;

export const rootReducer = combineReducers({
  notifications: notificationsReducer,
  [deleteDialogStateKey]: deleteDialogReducer,
  [currentUserStateKey]: currentUserReducer,
});
