import React, { useEffect } from "react";
import { useHistory } from "react-router-dom";

import { SimplePlaceholder } from "@project-openubl/lib-ui";

import {
  Bullseye,
  Card,
  CardBody,
  EmptyState,
  EmptyStateBody,
  EmptyStateIcon,
  EmptyStateVariant,
  PageSection,
  Title,
  Toolbar,
  ToolbarContent,
  ToolbarGroup,
  ToolbarItem,
} from "@patternfly/react-core";
import { SearchIcon } from "@patternfly/react-icons";

import {
  ConditionalRender,
  SimplePageSection,
  SearchInput,
  Welcome,
  ContribuyenteDetails,
} from "shared/components";
import { useFetchContribuyente, useFetchVersions } from "shared/hooks";

import { Paths } from "Paths";

export const ConsultaRuc: React.FC = () => {
  const history = useHistory();

  const {
    versions,
    isFetching: isFetchingVersions,
    fetchVersions,
  } = useFetchVersions();

  const {
    contribuyente,
    isFetching: isFetchingContribuyente,
    fetchError: contribuyenteFetchError,
    fetchContribuyente,
  } = useFetchContribuyente();

  useEffect(() => {
    fetchVersions({ watch: false, active: true });
  }, [fetchVersions]);

  const handleOnSearch = (ruc: string) => {
    if (ruc.trim().length > 0) {
      fetchContribuyente(ruc);
    }
  };

  const handleOnViewVersion = () => {
    history.push(Paths.versionList);
  };

  if (isFetchingVersions || (versions && versions.length === 0)) {
    return (
      <Bullseye>
        <ConditionalRender
          when={isFetchingVersions}
          then={<SimplePlaceholder />}
        >
          <Welcome onPrimaryAction={handleOnViewVersion} />
        </ConditionalRender>
      </Bullseye>
    );
  }

  return (
    <>
      <SimplePageSection
        title="Buscar por 'Número de documento'"
        description="Ingresa el número de RUC o DNI que deseas consultar."
      />
      <PageSection>
        <div
          style={{ backgroundColor: "var(--pf-global--BackgroundColor--100)" }}
        >
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
                when={isFetchingContribuyente}
                then={<SimplePlaceholder />}
              >
                <ConditionalRender
                  when={!!contribuyenteFetchError}
                  then={
                    <EmptyState variant={EmptyStateVariant.small}>
                      <EmptyStateIcon icon={SearchIcon} />
                      <Title headingLevel="h2" size="lg">
                        No se encontraron resultados
                      </Title>
                      <EmptyStateBody>
                        No se encontraron resultados que coincidan con el
                        criterio de búsqueda.
                      </EmptyStateBody>
                    </EmptyState>
                  }
                >
                  {contribuyente && (
                    <ContribuyenteDetails value={contribuyente} />
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
