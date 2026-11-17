import type React from "react";

import { Link } from "@tanstack/react-router";

import {
  Breadcrumb,
  BreadcrumbItem,
  Card,
  CardBody,
  Content,
  ContentVariants,
  PageSection,
} from "@patternfly/react-core";

import { ContribuyenteFields } from "@app/components/ContribuyenteFields";
import { DocumentTitle } from "@app/components/DocumentTitle";
import { useSuspenseContribuyenteDetailQuery } from "@app/queries/searchpe/contribuyentes";

interface IContribuyenteDetailProps {
  numeroDocumento: string;
}

/** One contribuyente of the active padrón version. */
export const ContribuyenteDetail: React.FC<IContribuyenteDetailProps> = ({
  numeroDocumento,
}) => {
  const { data: contribuyente } = useSuspenseContribuyenteDetailQuery({
    numeroDocumento,
  });
  const title = contribuyente.nombre || numeroDocumento;

  return (
    <>
      <DocumentTitle title={title} />
      <PageSection>
        <Breadcrumb>
          <BreadcrumbItem>
            <Link to="/padron/contribuyentes">Contribuyentes</Link>
          </BreadcrumbItem>
          <BreadcrumbItem isActive>{numeroDocumento}</BreadcrumbItem>
        </Breadcrumb>
        <Content component={ContentVariants.h1}>{title}</Content>
      </PageSection>
      <PageSection>
        <Card>
          <CardBody>
            <ContribuyenteFields contribuyente={contribuyente} />
          </CardBody>
        </Card>
      </PageSection>
    </>
  );
};
