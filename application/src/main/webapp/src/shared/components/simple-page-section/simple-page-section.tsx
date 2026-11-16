import React from "react";
import { PageSection, Content } from "@patternfly/react-core";

export interface SimplePageSectionProps {
  title: string;
  description?: string;
}

export const SimplePageSection: React.FC<SimplePageSectionProps> = ({
  title,
  description,
}) => {
  return (
    <PageSection hasBodyWrapper={false}>
      <Content>
        <Content component="h1">{title}</Content>
        {description && <Content component="small">{description}</Content>}
      </Content>
    </PageSection>
  );
};
