import React from "react";
import {
  DescriptionList,
  DescriptionListGroup,
  DescriptionListTerm,
  DescriptionListDescription,
} from "@patternfly/react-core";
import { Contribuyente } from "api/models";

export interface ContribuyenteDetailsProps {
  value: Contribuyente;
}

export const ContribuyenteDetails: React.FC<ContribuyenteDetailsProps> = ({
  value,
}) => {
  return (
    <DescriptionList columnModifier={{ lg: "3Col" }}>
      <DescriptionListGroup>
        <DescriptionListTerm>RUC</DescriptionListTerm>
        <DescriptionListDescription>{value.ruc}</DescriptionListDescription>
      </DescriptionListGroup>
      <DescriptionListGroup>
        <DescriptionListTerm>Razón social</DescriptionListTerm>
        <DescriptionListDescription>
          {value.razonSocial}
        </DescriptionListDescription>
      </DescriptionListGroup>
      <DescriptionListGroup>
        <DescriptionListTerm>Estado contribuyente</DescriptionListTerm>
        <DescriptionListDescription>
          {value.estadoContribuyente}
        </DescriptionListDescription>
      </DescriptionListGroup>
      <DescriptionListGroup>
        <DescriptionListTerm>Ubigeo</DescriptionListTerm>
        <DescriptionListDescription>{value.ubigeo}</DescriptionListDescription>
      </DescriptionListGroup>
      <DescriptionListGroup>
        <DescriptionListTerm>Condición domicilio</DescriptionListTerm>
        <DescriptionListDescription>
          {value.condicionDomicilio}
        </DescriptionListDescription>
      </DescriptionListGroup>
      <DescriptionListGroup>
        <DescriptionListTerm>Código zona</DescriptionListTerm>
        <DescriptionListDescription>
          {value.codigoZona}
        </DescriptionListDescription>
      </DescriptionListGroup>
      <DescriptionListGroup>
        <DescriptionListTerm>Tipo zona</DescriptionListTerm>
        <DescriptionListDescription>
          {value.tipoZona}
        </DescriptionListDescription>
      </DescriptionListGroup>
      <DescriptionListGroup>
        <DescriptionListTerm>Tipo via</DescriptionListTerm>
        <DescriptionListDescription>{value.tipoVia}</DescriptionListDescription>
      </DescriptionListGroup>
      <DescriptionListGroup>
        <DescriptionListTerm>Nombre via</DescriptionListTerm>
        <DescriptionListDescription>
          {value.nombreVia}
        </DescriptionListDescription>
      </DescriptionListGroup>
      <DescriptionListGroup>
        <DescriptionListTerm>Número</DescriptionListTerm>
        <DescriptionListDescription>{value.numero}</DescriptionListDescription>
      </DescriptionListGroup>
      <DescriptionListGroup>
        <DescriptionListTerm>Manzana</DescriptionListTerm>
        <DescriptionListDescription>{value.manzana}</DescriptionListDescription>
      </DescriptionListGroup>
      <DescriptionListGroup>
        <DescriptionListTerm>Lote</DescriptionListTerm>
        <DescriptionListDescription>{value.lote}</DescriptionListDescription>
      </DescriptionListGroup>
      <DescriptionListGroup>
        <DescriptionListTerm>Interior</DescriptionListTerm>
        <DescriptionListDescription>
          {value.interior}
        </DescriptionListDescription>
      </DescriptionListGroup>
      <DescriptionListGroup>
        <DescriptionListTerm>Departamento</DescriptionListTerm>
        <DescriptionListDescription>
          {value.departamento}
        </DescriptionListDescription>
      </DescriptionListGroup>
      <DescriptionListGroup>
        <DescriptionListTerm>Kilómetro</DescriptionListTerm>
        <DescriptionListDescription>
          {value.kilometro}
        </DescriptionListDescription>
      </DescriptionListGroup>
    </DescriptionList>
  );
};
