import { RootState } from "../rootReducer";
import { stateKey } from "./reducer";

export const currentUserState = (state: RootState) => state[stateKey];

export const user = (state: RootState) => currentUserState(state).user;
