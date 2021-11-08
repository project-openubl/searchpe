import { useSelector } from "react-redux";
import { RootState } from "store/rootReducer";
import { currentUserSelectors } from "store/currentUser";
import { Permission } from "Constants";

export interface IArgs {
  hasAny: Permission[];
}

export interface IState {
  isAllowed: boolean;
}

export const usePermission = ({ hasAny }: IArgs): IState => {
  const currentUser = useSelector((state: RootState) =>
    currentUserSelectors.user(state)
  );

  const userPermissions = currentUser?.permissions || [];
  const isAllowed = hasAny.some((permission) => {
    return userPermissions.some((f) => f === permission);
  });

  return {
    isAllowed,
  };
};

export default usePermission;
