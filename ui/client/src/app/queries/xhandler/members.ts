import type { UseQueryOptions } from "@tanstack/react-query";
import {
  queryOptions,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import {
  type AddMember,
  addMember,
  listMembers,
  type MemberView,
} from "@app/client/xhandler";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";

import type { IMutationCallbacks } from "../utils/mutationCallbacks";
import type { XhandlerAxiosError } from "../utils/openapiErrorTypes";

export const MembersQueryKey = "members";

/** Member kinds (`AddMember.kind`): a person, or a machine client by its Keycloak client id. */
export const MEMBER_KINDS = ["user", "client"] as const;

export type MemberKind = (typeof MEMBER_KINDS)[number];

/** Roles a member can be given (`AddMember.role`); owner and support aren't granted here. */
export const ASSIGNABLE_MEMBER_ROLES = ["admin", "operator", "viewer"] as const;

export type AssignableMemberRole = (typeof ASSIGNABLE_MEMBER_ROLES)[number];

export const memberKeys = {
  all: () => [MembersQueryKey] as const,
  list: (tenantId: string) => [...memberKeys.all(), tenantId, "list"] as const,
};

type MemberListOptions = Omit<
  UseQueryOptions<MemberView[], XhandlerAxiosError>,
  "queryKey" | "queryFn"
>;

export const membersListQueryOptions = ({
  tenantId,
  options,
}: {
  tenantId: string;
  options?: MemberListOptions;
}) =>
  queryOptions<MemberView[], XhandlerAxiosError>({
    ...options,
    queryKey: memberKeys.list(tenantId),
    queryFn: async () =>
      (
        await listMembers({
          path: { tenantId },
          throwOnError: true,
        })
      ).data,
  });

/** `GET …/members` (admin): unpaged by the API (one row per member), so it is read whole. */
export const useMembersListQuery = ({
  options,
}: {
  options?: MemberListOptions;
} = {}) => {
  const tenantId = useSelectedTenantId();
  return useQuery(membersListQueryOptions({ tenantId, options }));
};

/** `POST …/members` (admin): `201` with the member, `409 member_exists` for a duplicate. */
export const useMemberAddMutation = (
  options?: IMutationCallbacks<MemberView, AddMember, XhandlerAxiosError>,
) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return useMutation<MemberView, XhandlerAxiosError, AddMember>({
    mutationFn: async (body) =>
      (
        await addMember({
          path: { tenantId },
          body,
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, variables) => {
      await queryClient.invalidateQueries({
        queryKey: memberKeys.list(tenantId),
      });
      await options?.onSuccess?.(data, variables);
    },
    onError: options?.onError,
  });
};
