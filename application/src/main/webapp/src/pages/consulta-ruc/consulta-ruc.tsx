import React, { useState } from "react";

import {
  Card,
  CardBody,
  EmptyState,
  EmptyStateBody,
  EmptyStateVariant,
  PageSection,
  Toolbar,
  ToolbarContent,
  ToolbarGroup,
  ToolbarItem,
} from "@patternfly/react-core";
import { SearchIcon } from "@patternfly/react-icons";
import { t_global_background_color_primary_default as backgroundColor } from "@patternfly/react-tokens";

import { useContribuyenteQuery } from "queries/contribuyentes";
import {
  ConditionalRender,
  ContribuyenteDetails,
  SearchInput,
  SimplePageSection,
  SimplePlaceholder,
} from "shared/components";

export const ConsultaRuc: React.FC = () => {
  const [numeroDocumento, setNumeroDocumento] = useState("");
  const contribuyente = useContribuyenteQuery(numeroDocumento);

  const handleOnSearch = (numeroDocumento: string) => {
    if (numeroDocumento.trim().length > 0) {
      setNumeroDocumento(numeroDocumento);
    }
  };

  return (
    <>
      <SimplePageSection
        title="Buscar por 'Número de documento'"
        description="Ingresa el número de RUC o DNI que deseas consultar."
      />
      <PageSection hasBodyWrapper={false}>
        <div style={{ backgroundColor: backgroundColor.var }}>
          <Toolbar>
            <ToolbarContent>
              <ToolbarGroup>
                <ToolbarItem>
                  <SearchInput onSearch={handleOnSearch} />
                </ToolbarItem>
              </ToolbarGroup>
            </ToolbarContent>
          </Toolbar>
          <Card>
            <CardBody>
              <ConditionalRender
                when={contribuyente.isFetching}
                then={<SimplePlaceholder />}
              >
                <ConditionalRender
                  when={!!contribuyente.isError}
                  then={
                    <EmptyState
                      headingLevel="h2"
                      icon={SearchIcon}
                      titleText="No se encontraron resultados"
                      variant={EmptyStateVariant.sm}
                    >
                      <EmptyStateBody>
                        No se encontraron resultados que coincidan con el
                        criterio de búsqueda.
                      </EmptyStateBody>
                    </EmptyState>
                  }
                >
                  {contribuyente.data && (
                    <ContribuyenteDetails value={contribuyente.data} />
                  )}
                </ConditionalRender>
              </ConditionalRender>
            </CardBody>
          </Card>
        </div>
      </PageSection>
    </>
  );
};
