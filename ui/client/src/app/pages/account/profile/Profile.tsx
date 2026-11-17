import type React from "react";

import {
  Button,
  Card,
  CardBody,
  CardTitle,
  Content,
  ContentVariants,
  DescriptionList,
  DescriptionListDescription,
  DescriptionListGroup,
  DescriptionListTerm,
  Gallery,
  Label,
  LabelGroup,
  PageSection,
  Skeleton,
} from "@patternfly/react-core";
import ExternalLinkAltIcon from "@patternfly/react-icons/dist/esm/icons/external-link-alt-icon";

import { DocumentTitle } from "@app/components/DocumentTitle";
import { useApiStatus } from "@app/hooks/useApiStatus";
import { useAuth } from "@app/hooks/useAuth";
import { TENANT_ROLE_LABELS, type TenantRole } from "@app/hooks/useTenantRole";
import { OIDC_ACCOUNT_URL } from "@app/oidc";
import { useWhoamiQuery } from "@app/queries/searchpe/whoami";
import { useMyTenantsQuery } from "@app/queries/xhandler/tenants";

/**
 * Who the user is to each backend. Users live in Keycloak, so name, password and 2FA change in
 * its account console (ADR 0004).
 */
export const Profile: React.FC = () => {
  const auth = useAuth();
  const { xhandler, searchpe } = useApiStatus();
  const whoami = useWhoamiQuery();
  const tenants = useMyTenantsQuery({
    options: { enabled: xhandler.isAvailable },
  });

  return (
    <>
      <DocumentTitle title="Mi perfil" />
      <PageSection>
        <Content component={ContentVariants.h1}>Mi perfil</Content>
        <Content component={ContentVariants.p}>
          Tu cuenta es la del inicio de sesión único de Openubl.
        </Content>
      </PageSection>
      <PageSection>
        <Gallery hasGutter minWidths={{ default: "100%", lg: "420px" }}>
          <Card>
            <CardTitle>Cuenta</CardTitle>
            <CardBody>
              <DescriptionList>
                <DescriptionListGroup>
                  <DescriptionListTerm>Usuario</DescriptionListTerm>
                  <DescriptionListDescription>
                    {auth.user?.username ?? "-"}
                  </DescriptionListDescription>
                </DescriptionListGroup>
                <DescriptionListGroup>
                  <DescriptionListTerm>Datos y seguridad</DescriptionListTerm>
                  <DescriptionListDescription>
                    <Button
                      component="a"
                      href={OIDC_ACCOUNT_URL}
                      target="_blank"
                      rel="noopener noreferrer"
                      variant="link"
                      isInline
                      icon={<ExternalLinkAltIcon />}
                      iconPosition="end"
                    >
                      Gestionar mi cuenta
                    </Button>
                  </DescriptionListDescription>
                </DescriptionListGroup>
              </DescriptionList>
            </CardBody>
          </Card>

          {searchpe.isEnabled ? (
            <Card>
              <CardTitle>Padrón RUC (searchpe)</CardTitle>
              <CardBody>
                <DescriptionList>
                  <DescriptionListGroup>
                    <DescriptionListTerm>Permisos</DescriptionListTerm>
                    <DescriptionListDescription>
                      {whoami.isLoading ? (
                        <Skeleton screenreaderText="Cargando permisos" />
                      ) : whoami.error ? (
                        "No se pudieron cargar tus permisos."
                      ) : (whoami.data?.permissions ?? []).length > 0 ? (
                        <LabelGroup aria-label="Permisos en searchpe">
                          {(whoami.data?.permissions ?? []).map(
                            (permission) => (
                              <Label key={permission} isCompact>
                                {permission}
                              </Label>
                            ),
                          )}
                        </LabelGroup>
                      ) : (
                        "Sin permisos"
                      )}
                    </DescriptionListDescription>
                  </DescriptionListGroup>
                </DescriptionList>
              </CardBody>
            </Card>
          ) : null}

          <Card>
            <CardTitle>Comprobantes electrónicos (xhandler)</CardTitle>
            <CardBody>
              <DescriptionList>
                <DescriptionListGroup>
                  <DescriptionListTerm>Organizaciones</DescriptionListTerm>
                  <DescriptionListDescription>
                    {tenants.isLoading ? (
                      <Skeleton screenreaderText="Cargando organizaciones" />
                    ) : tenants.error || !xhandler.isAvailable ? (
                      "No se pudieron cargar tus organizaciones."
                    ) : (tenants.data ?? []).length > 0 ? (
                      <LabelGroup
                        aria-label="Organizaciones y roles"
                        numLabels={5}
                      >
                        {(tenants.data ?? []).map((tenant) => (
                          <Label key={tenant.id} isCompact>
                            {tenant.name} ·{" "}
                            {TENANT_ROLE_LABELS[tenant.role as TenantRole] ??
                              tenant.role}
                          </Label>
                        ))}
                      </LabelGroup>
                    ) : (
                      "Aún no perteneces a ninguna organización."
                    )}
                  </DescriptionListDescription>
                </DescriptionListGroup>
              </DescriptionList>
            </CardBody>
          </Card>
        </Gallery>
      </PageSection>
    </>
  );
};
