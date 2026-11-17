import { Permission } from "Constants";

export interface PageQuery {
  page: number;
  perPage: number;
}

export interface SortByQuery {
  orderBy: string | undefined;
  orderDirection: "asc" | "desc";
}

export interface SearchResult<T> {
  meta: Meta;
  links: Links;
  data: T[];
}

export interface Meta {
  offset: number;
  limit: number;
  count: number;
}

export interface Links {
  first: string;
  next: string;
  previous: string;
  last: string;
}

export type VersionStatus =
  | "SCHEDULED"
  | "DOWNLOADING"
  | "UNZIPPING"
  | "IMPORTING"
  | "INDEXING"
  | "ERROR"
  | "CANCELLED"
  | "COMPLETED"
  | "DELETING"
  | "CANCELLING";

export interface Version {
  id: number;
  createdAt: string;
  updatedAt: string;
  status: VersionStatus;
  records: number;
  active: boolean;
}

export interface Contribuyente {
  ruc: string;
  dni?: string;
  nombre: string;
  estado: string;
  ubigeo: string;
  condicionDomicilio: string;
  tipoVia: string;
  nombreVia: string;
  codigoZona: string;
  tipoZona: string;
  numero: string;
  interior: string;
  lote: string;
  departamento: string;
  manzana: string;
  kilometro: string;
}

export interface User {
  id?: number;
  fullName?: string;
  username: string;
  password?: string;
  permissions?: Permission[];
  authenticationScheme?: "Basic" | "Bearer";
}

export interface UserPasswordChange {
  newPassword: string;
}
