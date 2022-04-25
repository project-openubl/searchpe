import { isAuthDisabled, Permission } from "Constants";
import { useWhoAmIQuery } from "queries/whoami";

export interface IArgs {
  hasAny: Permission[];
}

export interface IState {
  isAllowed: boolean;
}

export const usePermission = ({ hasAny }: IArgs): IState => {
  const whoAmI = useWhoAmIQuery();

  const userPermissions = whoAmI.data?.permissions || [];
  const isAllowed = hasAny.some((permission) => {
    return userPermissions.some((f) => f === permission);
  });

  return {
    isAllowed: isAllowed || isAuthDisabled(),
  };
};

export default usePermission;
