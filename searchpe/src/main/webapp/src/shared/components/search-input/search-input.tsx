import React, { useState } from "react";

import {
  Button,
  InputGroup,
  TextInput,
  InputGroupItem,
} from "@patternfly/react-core";
import { SearchIcon } from "@patternfly/react-icons";

export interface SearchInputProps {
  onSearch: (filterText: string) => void;
}

export const SearchInput: React.FC<SearchInputProps> = ({ onSearch }) => {
  const [filterText, setFilterText] = useState("");

  const callSearch = () => {
    onSearch(filterText);
  };

  const handleOnKeyDown = (event: React.KeyboardEvent) => {
    if (event.key === "Enter") {
      callSearch();
    }
  };

  return (
    <InputGroup>
      <InputGroupItem isFill>
        <TextInput
          name="filterText"
          type="search"
          aria-label="search input"
          value={filterText}
          onChange={(_event, val) => setFilterText(val)}
          onKeyDown={handleOnKeyDown}
          autoComplete="off"
        />
      </InputGroupItem>
      <InputGroupItem>
        <Button
          icon={<SearchIcon />}
          variant="control"
          aria-label="search button"
          onClick={callSearch}
        />
      </InputGroupItem>
    </InputGroup>
  );
};
