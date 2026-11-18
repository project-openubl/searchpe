import {
  queryOptions,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import {
  type CertificateView,
  getCertificate,
  uploadCertificate,
} from "@app/client/xhandler";
import { useSelectedTenantId } from "@app/hooks/useSelectedTenantId";
import { isNotFoundError } from "@app/utils/isHttpError";

import type { IMutationCallbacks } from "../utils/mutationCallbacks";
import type { XhandlerAxiosError } from "../utils/openapiErrorTypes";

export const CertificatesQueryKey = "certificates";

export const certificateKeys = {
  all: () => [CertificatesQueryKey] as const,
  detail: (tenantId: string, companyId: string) =>
    [...certificateKeys.all(), tenantId, companyId] as const,
};

/** Days left before a certificate expires (negative once expired). */
export const certificateDaysLeft = ({
  validUntil,
  now = new Date(),
}: {
  validUntil: string | undefined;
  now?: Date;
}): number | undefined => {
  if (!validUntil) return undefined;
  const millis = new Date(validUntil).getTime() - now.getTime();
  return Math.floor(millis / (24 * 60 * 60 * 1000));
};

/** The certificate's standing: SUNAT refuses signatures from an expired one; notices start at 30 days. */
export type CertificateStanding = "valid" | "expiring" | "expired";

export const certificateStanding = (
  daysLeft: number | undefined,
): CertificateStanding | undefined => {
  if (daysLeft === undefined) return undefined;
  if (daysLeft < 0) return "expired";
  return daysLeft <= 30 ? "expiring" : "valid";
};

export const certificateQueryOptions = ({
  tenantId,
  companyId,
}: {
  tenantId: string;
  companyId: string;
}) =>
  queryOptions<CertificateView, XhandlerAxiosError>({
    queryKey: certificateKeys.detail(tenantId, companyId),
    queryFn: async () =>
      (
        await getCertificate({
          path: { tenantId, companyId },
          throwOnError: true,
        })
      ).data,
    // A company without a certificate answers 404; asking again won't change it.
    retry: (failureCount, error) => !isNotFoundError(error) && failureCount < 3,
  });

/** `GET …/certificate`: the active certificate (never its key). */
export const useCertificateQuery = ({ companyId }: { companyId: string }) => {
  const tenantId = useSelectedTenantId();
  return useQuery({
    ...certificateQueryOptions({ tenantId, companyId }),
    enabled: !!tenantId && !!companyId,
  });
};

export interface ICertificateUpload {
  file: File;
  password: string;
}

/** `PUT …/certificate` (admin, multipart): replaces the active key; the password is never kept. */
export const useCertificateUploadMutation = (
  companyId: string,
  options?: IMutationCallbacks<
    CertificateView,
    ICertificateUpload,
    XhandlerAxiosError
  >,
) => {
  const tenantId = useSelectedTenantId();
  const queryClient = useQueryClient();
  return useMutation<CertificateView, XhandlerAxiosError, ICertificateUpload>({
    mutationFn: async ({ file, password }) =>
      (
        await uploadCertificate({
          path: { tenantId, companyId },
          body: { file, password },
          throwOnError: true,
        })
      ).data,
    onSuccess: async (data, variables) => {
      queryClient.setQueryData(
        certificateKeys.detail(tenantId, companyId),
        data,
      );
      await options?.onSuccess?.(data, variables);
    },
    onError: options?.onError,
  });
};
