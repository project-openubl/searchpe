import type React from "react";

import {
  DescriptionList,
  DescriptionListDescription,
  DescriptionListGroup,
  DescriptionListTerm,
} from "@patternfly/react-core";

import type { ContribuyenteDto } from "@app/client/searchpe";

import { EstadoContribuyenteLabel } from "./EstadoContribuyenteLabel";

interface IContribuyenteFieldsProps {
  contribuyente: ContribuyenteDto;
}

/** The padrón columns, in SUNAT's order; an empty column reads "-". */
const FIELDS: { key: keyof ContribuyenteDto; label: string }[] = [
  { key: "ubigeo", label: "Ubigeo" },
  { key: "tipoVia", label: "Tipo de vía" },
  { key: "nombreVia", label: "Nombre de vía" },
  { key: "codigoZona", label: "Código de zona" },
  { key: "tipoZona", label: "Tipo de zona" },
  { key: "numero", label: "Número" },
  { key: "interior", label: "Interior" },
  { key: "lote", label: "Lote" },
  { key: "departamento", label: "Departamento" },
  { key: "manzana", label: "Manzana" },
  { key: "kilometro", label: "Kilómetro" },
];

const orDash = (value: unknown): string =>
  typeof value === "string" && value.trim() && value.trim() !== "-"
    ? value.trim()
    : "-";

/** A contribuyente's padrón data (consulta RUC and the contribuyente page). */
export const ContribuyenteFields: React.FC<IContribuyenteFieldsProps> = ({
  contribuyente,
}) => (
  <DescriptionList
    aria-label="Datos del contribuyente"
    columnModifier={{ md: "2Col", lg: "3Col" }}
  >
    <DescriptionListGroup>
      <DescriptionListTerm>RUC</DescriptionListTerm>
      <DescriptionListDescription>
        {orDash(contribuyente.ruc)}
      </DescriptionListDescription>
    </DescriptionListGroup>
    <DescriptionListGroup>
      <DescriptionListTerm>DNI</DescriptionListTerm>
      <DescriptionListDescription>
        {orDash(contribuyente.dni)}
      </DescriptionListDescription>
    </DescriptionListGroup>
    <DescriptionListGroup>
      <DescriptionListTerm>Nombre o razón social</DescriptionListTerm>
      <DescriptionListDescription>
        {orDash(contribuyente.nombre)}
      </DescriptionListDescription>
    </DescriptionListGroup>
    <DescriptionListGroup>
      <DescriptionListTerm>Estado</DescriptionListTerm>
      <DescriptionListDescription>
        <EstadoContribuyenteLabel estado={contribuyente.estado} />
      </DescriptionListDescription>
    </DescriptionListGroup>
    <DescriptionListGroup>
      <DescriptionListTerm>Condición de domicilio</DescriptionListTerm>
      <DescriptionListDescription>
        {orDash(contribuyente.condicionDomicilio)}
      </DescriptionListDescription>
    </DescriptionListGroup>
    {FIELDS.map(({ key, label }) => (
      <DescriptionListGroup key={key}>
        <DescriptionListTerm>{label}</DescriptionListTerm>
        <DescriptionListDescription>
          {orDash(contribuyente[key])}
        </DescriptionListDescription>
      </DescriptionListGroup>
    ))}
  </DescriptionList>
);
