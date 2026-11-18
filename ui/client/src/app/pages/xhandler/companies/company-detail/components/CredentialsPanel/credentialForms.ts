import { boolean, object, string } from "yup";

import type {
  CredentialKind,
  CredentialWrite,
} from "@app/queries/xhandler/credentials";

/** Every field any kind's form has; each kind shows and sends its own. */
export interface ICredentialFormValues {
  username: string;
  password: string;
  client_id: string;
  client_secret: string;
  host: string;
  port: string;
  from: string;
  start_tls: boolean;
  url: string;
  token: string;
}

export type CredentialField = Exclude<keyof ICredentialFormValues, "start_tls">;

/** Write-only: forms always start empty, the stored secret is never shown or prefilled. */
export const EMPTY_CREDENTIAL_VALUES: ICredentialFormValues = {
  username: "",
  password: "",
  client_id: "",
  client_secret: "",
  host: "",
  port: "587",
  from: "",
  start_tls: true,
  url: "",
  token: "",
};

interface IFieldPresentation {
  label: string;
  isSecret?: boolean;
  isRequired?: boolean;
  helperText?: string;
}

export interface ICredentialPresentation {
  title: string;
  description: string;
  fields: Partial<Record<CredentialField, IFieldPresentation>>;
  hasStartTls?: boolean;
}

export const CREDENTIAL_PRESENTATION: Record<
  CredentialKind,
  ICredentialPresentation
> = {
  sol: {
    title: "Clave SOL",
    description:
      "Usuario secundario SOL con el que se envían los comprobantes y se verifica el RUC.",
    fields: {
      username: {
        label: "Usuario SOL",
        isRequired: true,
        helperText: "El RUC seguido del usuario (20123456789MODDATOS).",
      },
      password: { label: "Clave SOL", isSecret: true, isRequired: true },
    },
  },
  gre: {
    title: "Guías de remisión (GRE)",
    description:
      "Usuario SOL y credenciales de la API de SUNAT para enviar guías de remisión.",
    fields: {
      username: { label: "Usuario SOL", isRequired: true },
      password: { label: "Clave SOL", isSecret: true, isRequired: true },
      client_id: { label: "Client ID", isRequired: true },
      client_secret: {
        label: "Client secret",
        isSecret: true,
        isRequired: true,
      },
    },
  },
  sire: {
    title: "SIRE",
    description:
      "Usuario SOL con perfil SIRE y credenciales de la API de SUNAT para los registros de ventas y compras.",
    fields: {
      username: { label: "Usuario SOL", isRequired: true },
      password: { label: "Clave SOL", isSecret: true, isRequired: true },
      client_id: { label: "Client ID", isRequired: true },
      client_secret: {
        label: "Client secret",
        isSecret: true,
        isRequired: true,
      },
    },
  },
  smtp: {
    title: "Servidor de correo (SMTP)",
    description:
      "Tu propio servidor para enviar los comprobantes por correo; según tu plan.",
    fields: {
      host: { label: "Servidor", isRequired: true },
      port: { label: "Puerto", isRequired: true },
      username: { label: "Usuario", isRequired: true },
      password: { label: "Contraseña", isSecret: true, isRequired: true },
      from: { label: "Remitente", isRequired: true },
    },
    hasStartTls: true,
  },
  lookup: {
    title: "Consulta RUC/DNI",
    description:
      "Un proveedor comercial de consulta de RUC y DNI; la URL lleva {tipo} y {numero}.",
    fields: {
      url: { label: "URL", isRequired: true },
      token: {
        label: "Token",
        isSecret: true,
        helperText: "Opcional, si el proveedor lo pide.",
      },
    },
  },
};

const required = (message: string) => string().trim().required(message);
const optional = string().defined().trim();

/** The yup schema of one kind's form: its fields required as the API requires them. */
export const credentialSchema = (kind: CredentialKind) => {
  const fields = CREDENTIAL_PRESENTATION[kind].fields;
  const field = (name: CredentialField, message: string) =>
    fields[name]?.isRequired ? required(message) : optional;
  return object({
    username: field("username", "Ingresa el usuario").max(
      200,
      "Como máximo 200 caracteres",
    ),
    password: field("password", "Ingresa la contraseña").max(
      300,
      "Como máximo 300 caracteres",
    ),
    client_id: field("client_id", "Ingresa el client ID"),
    client_secret: field("client_secret", "Ingresa el client secret"),
    host: field("host", "Ingresa el servidor").max(
      255,
      "Como máximo 255 caracteres",
    ),
    port: fields.port
      ? required("Ingresa el puerto").matches(
          /^\d{1,5}$/,
          "El puerto es un número entre 1 y 65535",
        )
      : optional,
    from: fields.from
      ? required("Ingresa el remitente").email("Ingresa un correo válido")
      : optional,
    start_tls: boolean().required(),
    // Not yup's `url()`: the template's `{tipo}` and `{numero}` aren't valid URL characters.
    url: fields.url
      ? required("Ingresa la URL").matches(
          /^https?:\/\/\S+$/,
          "Ingresa una URL válida",
        )
      : optional,
    token: optional.max(500, "Como máximo 500 caracteres"),
  });
};

/** The request body of one kind, from its form's values. */
export const toCredentialWrite = ({
  kind,
  values,
}: {
  kind: CredentialKind;
  values: ICredentialFormValues;
}): CredentialWrite => {
  switch (kind) {
    case "sol":
      return {
        kind,
        body: { username: values.username, password: values.password },
      };
    case "gre":
    case "sire":
      return {
        kind,
        body: {
          username: values.username,
          password: values.password,
          client_id: values.client_id,
          client_secret: values.client_secret,
        },
      };
    case "smtp":
      return {
        kind,
        body: {
          host: values.host,
          port: Number(values.port),
          username: values.username,
          password: values.password,
          from: values.from,
          start_tls: values.start_tls,
        },
      };
    case "lookup":
      return {
        kind,
        body: { url: values.url, token: values.token || undefined },
      };
  }
};
