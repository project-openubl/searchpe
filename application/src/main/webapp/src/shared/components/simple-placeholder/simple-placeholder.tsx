import React from "react";
import {
  Bullseye,
  Content,
  Flex,
  FlexItem,
  Spinner,
} from "@patternfly/react-core";

// Inlined from @project-openubl/lib-ui

export const SimplePlaceholder: React.FC = () => {
  return (
    <Bullseye>
      <Flex direction={{ default: "column" }}>
        <FlexItem>
          <Spinner aria-label="Loading" />
        </FlexItem>
        <FlexItem>
          <Content component="h3">Loading...</Content>
        </FlexItem>
      </Flex>
    </Bullseye>
  );
};
