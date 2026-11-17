import type React from "react";

import {
  Bullseye,
  Content,
  Spinner,
  Stack,
  StackItem,
} from "@patternfly/react-core";

export const AppPlaceholder: React.FC = () => {
  return (
    <Bullseye>
      <Stack>
        <StackItem>
          <Spinner />
        </StackItem>
        <StackItem>
          <Content component="h3">Cargando…</Content>
        </StackItem>
      </Stack>
    </Bullseye>
  );
};
