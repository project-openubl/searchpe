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
  removeMember,
  updateMember,
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

/** A member add: the body, and the `Idempotency-Key` of this user intent. */
export interface IMemberAddVariables {
  body: AddMember;
  idempotencyKey: string;
}

/**
 * `POST …/members` (admin): `201` with the member (also a replay of the same key),
 * `409 member_exists` for a duplicate.
 */
export const useMemberAddMutation = (
  options?: IMutationCallbacks<
    MemberView,
    IMemberAddVariables,
    XhandlerAxiosError
  >,
) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return useMutation<MemberView, XhandlerAxiosError, IMemberAddVariables>({
    mutationFn: async ({ body, idempotencyKey }) =>
      (
        await addMember({
          path: { tenantId },
          body,
          headers: { "Idempotency-Key": idempotencyKey },
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

/** A role change: the member and its new role. */
export interface IMemberUpdateVariables {
  member: MemberView;
  role: AssignableMemberRole;
}

/**
 * `PUT …/members/{id}` (admin): changes the role. Only an owner changes an owner
 * (`403 owner_required`), and the last owner stays (`409 last_owner`).
 */
export const useMemberUpdateMutation = (
  options?: IMutationCallbacks<
    MemberView,
    IMemberUpdateVariables,
    XhandlerAxiosError
  >,
) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return useMutation<MemberView, XhandlerAxiosError, IMemberUpdateVariables>({
    mutationFn: async ({ member, role }) =>
      (
        await updateMember({
          path: { tenantId, memberId: member.id ?? "" },
          body: { role },
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

/** `DELETE …/members/{id}` (admin): `204`; the same owner rules as a role change. */
export const useMemberRemoveMutation = (
  options?: IMutationCallbacks<void, MemberView, XhandlerAxiosError>,
) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return useMutation<void, XhandlerAxiosError, MemberView>({
    mutationFn: async (member) => {
      await removeMember({
        path: { tenantId, memberId: member.id ?? "" },
        throwOnError: true,
      });
    },
    onSuccess: async (data, member) => {
      await queryClient.invalidateQueries({
        queryKey: memberKeys.list(tenantId),
      });
      await options?.onSuccess?.(data, member);
    },
    onError: options?.onError,
  });
};
