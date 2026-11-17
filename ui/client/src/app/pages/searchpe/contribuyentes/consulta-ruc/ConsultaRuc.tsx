import type React from "react";
import { useState } from "react";

import { getRouteApi } from "@tanstack/react-router";

import {
  Bullseye,
  Button,
  Card,
  CardBody,
  Content,
  ContentVariants,
  EmptyState,
  EmptyStateBody,
  Form,
  FormGroup,
  FormHelperText,
  HelperText,
  HelperTextItem,
  PageSection,
  Spinner,
  TextInput,
  Toolbar,
  ToolbarContent,
  ToolbarItem,
} from "@patternfly/react-core";
import ExclamationCircleIcon from "@patternfly/react-icons/dist/esm/icons/exclamation-circle-icon";
import SearchIcon from "@patternfly/react-icons/dist/esm/icons/search-icon";

import { ContribuyenteFields } from "@app/components/ContribuyenteFields";
import { DocumentTitle } from "@app/components/DocumentTitle";
import { UnauthorizedState } from "@app/components/UnauthorizedState";
import { useSearchpePermissions } from "@app/hooks/useSearchpePermissions";
import {
  isNumeroDocumento,
  useContribuyenteDetailQuery,
} from "@app/queries/searchpe/contribuyentes";
import { isForbiddenError, isNotFoundError } from "@app/utils/isHttpError";

const routeApi = getRouteApi("/_authenticated/padron/consulta");

/** Looks a RUC (11 digits) or DNI (8 digits) up in the active padrón version. */
export const ConsultaRuc: React.FC = () => {
  const { numero = "" } = routeApi.useSearch();
  const navigate = routeApi.useNavigate();
  const { can } = useSearchpePermissions();

  // Keyed on the URL's number, so going back or forward resets what is typed.
  const [typed, setTyped] = useState(numero);
  const [lastNumero, setLastNumero] = useState(numero);
  if (lastNumero !== numero) {
    setLastNumero(numero);
    setTyped(numero);
  }

  const trimmed = typed.trim();
  const isTypedValid = isNumeroDocumento(trimmed);

  const { data, isFetching, error } = useContribuyenteDetailQuery({
    numeroDocumento: numero,
  });

  const onSearch = () => {
    if (!isTypedValid) return;
    void navigate({ search: (prev) => ({ ...prev, numero: trimmed }) });
  };

  const renderResult = () => {
    if (!numero) {
      return (
        <EmptyState
          variant="sm"
          icon={SearchIcon}
          titleText="Ingresa un número de documento"
          headingLevel="h2"
        >
          <EmptyStateBody>
            Escribe un RUC de 11 dígitos o un DNI de 8 dígitos y pulsa Buscar.
          </EmptyStateBody>
        </EmptyState>
      );
    }
    if (!isNumeroDocumento(numero)) {
      return (
        <EmptyState
          variant="sm"
          icon={SearchIcon}
          titleText="Número de documento no válido"
          headingLevel="h2"
        >
          <EmptyStateBody>
            Un RUC tiene 11 dígitos y un DNI tiene 8 dígitos.
          </EmptyStateBody>
        </EmptyState>
      );
    }
    if (isFetching) {
      return (
        <Bullseye>
          <Spinner aria-label="Buscando contribuyente" />
        </Bullseye>
      );
    }
    if (isNotFoundError(error)) {
      return (
        <EmptyState
          variant="sm"
          icon={SearchIcon}
          titleText="No se encontraron resultados"
          headingLevel="h2"
        >
          <EmptyStateBody>
            El número {numero} no está en la versión activa del padrón.
          </EmptyStateBody>
        </EmptyState>
      );
    }
    if (isForbiddenError(error)) {
      return <UnauthorizedState />;
    }
    if (error) {
      return (
        <EmptyState
          variant="sm"
          status="danger"
          icon={ExclamationCircleIcon}
          titleText="No se pudo consultar el padrón"
          headingLevel="h2"
        >
          <EmptyStateBody>Inténtalo de nuevo en unos minutos.</EmptyStateBody>
        </EmptyState>
      );
    }
    return data ? <ContribuyenteFields contribuyente={data} /> : null;
  };

  return (
    <>
      <DocumentTitle title="Consulta RUC" />
      <PageSection>
        <Content component={ContentVariants.h1}>Consulta RUC</Content>
        <Content component={ContentVariants.p}>
          Busca un contribuyente por su RUC o DNI en el padrón reducido de la
          SUNAT.
        </Content>
      </PageSection>
      <PageSection>
        {can("search") ? (
          <Card>
            <CardBody>
              <Form
                id="consulta-ruc-form"
                onSubmit={(event) => {
                  event.preventDefault();
                  onSearch();
                }}
              >
                <Toolbar>
                  <ToolbarContent>
                    <ToolbarItem>
                      <FormGroup
                        label="Número de documento"
                        fieldId="numero-documento"
                      >
                        <TextInput
                          id="numero-documento"
                          value={typed}
                          inputMode="numeric"
                          autoComplete="off"
                          validated={
                            trimmed && !isTypedValid ? "error" : "default"
                          }
                          onChange={(_event, value) => setTyped(value)}
                        />
                        <FormHelperText>
                          <HelperText>
                            <HelperTextItem
                              variant={
                                trimmed && !isTypedValid ? "error" : "default"
                              }
                            >
                              RUC (11 dígitos) o DNI (8 dígitos)
                            </HelperTextItem>
                          </HelperText>
                        </FormHelperText>
                      </FormGroup>
                    </ToolbarItem>
                    <ToolbarItem alignSelf="center">
                      <Button
                        type="submit"
                        form="consulta-ruc-form"
                        isDisabled={!isTypedValid}
                      >
                        Buscar
                      </Button>
                    </ToolbarItem>
                  </ToolbarContent>
                </Toolbar>
              </Form>
              {renderResult()}
            </CardBody>
          </Card>
        ) : (
          <UnauthorizedState description="Necesitas el permiso search en searchpe para consultar el padrón." />
        )}
      </PageSection>
    </>
  );
};
