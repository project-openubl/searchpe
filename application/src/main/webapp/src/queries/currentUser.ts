import {
  UseMutationResult,
  useQueryClient,
  useMutation,
} from "@tanstack/react-query";

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
  return useMutation<void, ApiClientError, User>({
    mutationFn: async (user: User) => {
      return (await client.put<void>(profileResource, "", user)).data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["users"] });
      queryClient.invalidateQueries({ queryKey: ["whoami"] });
      onSuccess && onSuccess();
    },
    onError,
  });
};

export const useUpdateCurrentUserPasswordMutation = (
  onSuccess: () => void,
  onError: (err: AxiosError) => void
): UseMutationResult<void, ApiClientError, UserPasswordChange> => {
  const client = useSearchpeClient();
  return useMutation<void, ApiClientError, UserPasswordChange>({
    mutationFn: async (password: UserPasswordChange) => {
      return (await client.create<void>(credentialsResource, password)).data;
    },
    onSuccess: () => {
      onSuccess && onSuccess();
    },
    onError,
  });
};
