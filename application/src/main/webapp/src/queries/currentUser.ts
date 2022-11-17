import { UseMutationResult, useQueryClient, useMutation } from "react-query";

import {
  CurrentUserClusterResource,
  CurrentUserClusterResourceKind,
} from "api-client";
import { ApiClientError } from "api-client/types";

import { User, UserPasswordChange } from "api/models";
import { useSearchpeClient } from "./fetchHelpers";
import { AxiosError } from "axios";

const profileResource = new CurrentUserClusterResource(
  CurrentUserClusterResourceKind.Profile
);
const credentialsResource = new CurrentUserClusterResource(
  CurrentUserClusterResourceKind.Credentials
);

export const useUpdateCurrentUserProfileMutation = (
  onSuccess: () => void,
  onError: (err: AxiosError) => void
): UseMutationResult<void, ApiClientError, User> => {
  const client = useSearchpeClient();
  const queryClient = useQueryClient();
  return useMutation<void, ApiClientError, User>(
    async (user: User) => {
      return (await client.put<void>(profileResource, "", user)).data;
    },
    {
      onSuccess: () => {
        queryClient.invalidateQueries("users");
        queryClient.invalidateQueries("whoami");
        onSuccess && onSuccess();
      },
      onError,
    }
  );
};

export const useUpdateCurrentUserPasswordMutation = (
  onSuccess: () => void,
  onError: (err: AxiosError) => void
): UseMutationResult<void, ApiClientError, UserPasswordChange> => {
  const client = useSearchpeClient();
  return useMutation<void, ApiClientError, UserPasswordChange>(
    async (password: UserPasswordChange) => {
      return (await client.create<void>(credentialsResource, password)).data;
    },
    {
      onSuccess: () => {
        onSuccess && onSuccess();
      },
      onError,
    }
  );
};
