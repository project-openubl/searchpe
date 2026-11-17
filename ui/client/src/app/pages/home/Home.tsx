import type React from "react";

import { Link } from "@tanstack/react-router";

import {
  Alert,
  Card,
  CardBody,
  CardFooter,
  CardTitle,
  Content,
  ContentVariants,
  Gallery,
  Label,
  PageSection,
} from "@patternfly/react-core";

import { DocumentTitle } from "@app/components/DocumentTitle";
import { useApiStatus } from "@app/hooks/useApiStatus";
import { useAuth } from "@app/hooks/useAuth";

interface IServiceLabelProps {
  isLoading: boolean;
  isAvailable: boolean;
}

const ServiceLabel: React.FC<IServiceLabelProps> = ({
  isLoading,
  isAvailable,
}) =>
  isLoading ? (
    <Label isCompact>Comprobando…</Label>
  ) : isAvailable ? (
    <Label color="green" isCompact>
      Disponible
    </Label>
  ) : (
    <Label color="red" isCompact>
      No disponible
    </Label>
  );

/** The landing page: one card per product, with its availability (ADR 0001). */
export const Home: React.FC = () => {
  const auth = useAuth();
  const { xhandler, searchpe } = useApiStatus();

  return (
    <>
      <DocumentTitle title="Inicio" />
      <PageSection>
        <Content component={ContentVariants.h1}>
          Hola, {auth.user?.username ?? "bienvenido"}
        </Content>
        <Content component={ContentVariants.p}>
          Elige con qué quieres trabajar.
        </Content>
      </PageSection>
      <PageSection>
        <Gallery hasGutter minWidths={{ default: "100%", md: "360px" }}>
          <Card>
            <CardTitle>
              Comprobantes electrónicos{" "}
              <ServiceLabel
                isLoading={xhandler.isLoading}
                isAvailable={xhandler.isAvailable}
              />
            </CardTitle>
            <CardBody>
              Emite, envía y consulta comprobantes de pago electrónicos SUNAT de
              tus organizaciones y empresas.
              {!xhandler.isLoading && !xhandler.isAvailable ? (
                <Alert
                  variant="danger"
                  isInline
                  isPlain
                  title="xhandler-service no responde; sus páginas no están disponibles."
                />
              ) : null}
            </CardBody>
            {xhandler.isAvailable ? (
              <CardFooter>
                <Link to="/organizaciones">Ir a mis organizaciones</Link>
              </CardFooter>
            ) : null}
          </Card>
          {searchpe.isEnabled ? (
            <Card>
              <CardTitle>
                Padrón RUC{" "}
                <ServiceLabel
                  isLoading={searchpe.isLoading}
                  isAvailable={searchpe.isAvailable}
                />
              </CardTitle>
              <CardBody>
                Consulta contribuyentes por RUC o DNI en el padrón reducido de
                la SUNAT y administra sus versiones.
                {!searchpe.isLoading && !searchpe.isAvailable ? (
                  <Alert
                    variant="danger"
                    isInline
                    isPlain
                    title="searchpe no responde; sus páginas no están disponibles."
                  />
                ) : null}
              </CardBody>
              {searchpe.isAvailable ? (
                <CardFooter>
                  <Link to="/padron/consulta">Consultar un RUC</Link>
                </CardFooter>
              ) : null}
            </Card>
          ) : null}
        </Gallery>
      </PageSection>
    </>
  );
};
