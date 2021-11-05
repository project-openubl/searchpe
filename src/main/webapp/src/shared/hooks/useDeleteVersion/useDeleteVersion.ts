import { useCallback, useState } from "react";
import { AxiosError } from "axios";

import { deleteVersion } from "api/rest";
import { Version } from "api/models";

export interface IState {
  isDeleting: boolean;
  deleteVersion: (
    version: Version,
    onSuccess: () => void,
    onError: (error: AxiosError) => void
  ) => void;
}

export const useDeleteVersion = (): IState => {
  const [isDeleting, setIsDeleting] = useState(false);

  const deleteVersionHandler = useCallback(
    (
      version: Version,
      onSuccess: () => void,
      onError: (error: AxiosError) => void
    ) => {
      setIsDeleting(true);
      deleteVersion(version.id)
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
    deleteVersion: deleteVersionHandler,
  };
};

export default useDeleteVersion;
