import { useCallback, useState } from "react";
import { AxiosError } from "axios";

import { deleteUser } from "api/rest";
import { User } from "api/models";

export interface IState {
  isDeleting: boolean;
  deleteUser: (
    version: User,
    onSuccess: () => void,
    onError: (error: AxiosError) => void
  ) => void;
}

export const useDeleteUser = (): IState => {
  const [isDeleting, setIsDeleting] = useState(false);

  const deleteUserHandler = useCallback(
    (
      user: User,
      onSuccess: () => void,
      onError: (error: AxiosError) => void
    ) => {
      setIsDeleting(true);
      deleteUser(user.id!)
        .then(() => {
          setIsDeleting(false);
          onSuccess();
        })
        .catch((error: AxiosError) => {
          setIsDeleting(false);
          onError(error);
        });
    },
    []
  );

  return {
    isDeleting,
    deleteUser: deleteUserHandler,
  };
};

export default useDeleteUser;
