import type React from "react";

import dayjs from "dayjs";

import { Alert, type AlertProps } from "@patternfly/react-core";

import type { OseLookupResult } from "@app/client/xhandler";
import { RENDER_DATETIME_FORMAT } from "@app/constants";
import {
  isOseLookupState,
  type OseLookupState,
} from "@app/queries/xhandler/public-lookup";

interface IOseLookupResultAlertProps {
  result: OseLookupResult;
}

const PRESENTATION: Record<
  OseLookupState,
  {
    variant: AlertProps["variant"];
    title: string;
    body: (checkedAt: string | undefined) => string;
  }
> = {
  con_cdr: {
    variant: "success",
    title: "Comprobante válido",
    body: (checkedAt) =>
      checkedAt
        ? `El OSE emitió la constancia de recepción (CDR) el ${checkedAt}.`
        : "El OSE emitió la constancia de recepción (CDR).",
  },
  con_inconsistencia: {
    variant: "warning",
    title: "Comprobante con inconsistencias",
    body: (checkedAt) =>
      checkedAt
        ? `El OSE revisó el comprobante el ${checkedAt} y comunicó inconsistencias.`
        : "El OSE revisó el comprobante y comunicó inconsistencias.",
  },
  no_encontrado: {
    variant: "info",
    title: "Comprobante no encontrado",
    body: () =>
      "El OSE no tiene registro de este comprobante. Revisa los datos ingresados.",
  },
};

/** The server sends `checked_at` as text, "null" when it has none. */
const formatCheckedAt = (value: string | undefined): string | undefined => {
  const date = value ? dayjs(value) : undefined;
  return date?.isValid() ? date.format(RENDER_DATETIME_FORMAT) : undefined;
};

/** The answer of the OSE validity query; a state the UI doesn't know shows as returned. */
export const OseLookupResultAlert: React.FC<IOseLookupResultAlertProps> = ({
  result,
}) => {
  const state = result.state ?? "";
  const checkedAt = formatCheckedAt(result.checked_at);
  if (!isOseLookupState(state)) {
    return (
      <Alert variant="custom" isInline title="Resultado de la consulta">
        {state || "-"}
      </Alert>
    );
  }
  const { variant, title, body } = PRESENTATION[state];
  return (
    <Alert variant={variant} isInline title={title} aria-live="polite">
      {body(checkedAt)}
    </Alert>
  );
};
