import { applyMiddleware, createStore } from "redux";
import thunk from "redux-thunk";
import { rootReducer } from "./rootReducer";

export const mockStore = (initialStatus?: any) =>
  initialStatus
    ? createStore(rootReducer, initialStatus, applyMiddleware(thunk))
    : createStore(rootReducer, applyMiddleware(thunk));
