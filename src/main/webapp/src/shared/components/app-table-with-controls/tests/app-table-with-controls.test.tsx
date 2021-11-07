import React from "react";
import { mount, shallow } from "enzyme";
import { ICell } from "@patternfly/react-table";

import { AppTableWithControls } from "../app-table-with-controls";

describe("AppTableWithControls", () => {
  const columns: ICell[] = [{ title: "Col1" }];
  const itemsToRow = (items: string[]) => {
    return items.map((item) => ({
      cells: [
        {
          title: item,
        },
      ],
    }));
  };

  it("Renders without crashing", () => {
    const wrapper = shallow(
      <AppTableWithControls
        count={120}
        items={["first", "second", "third"]}
        itemsToRow={itemsToRow}
        pagination={{ page: 1, perPage: 10 }}
        handleFilterTextChange={jest.fn()}
        handlePaginationChange={jest.fn()}
        handleSortChange={jest.fn()}
        columns={columns}
        isLoading={false}
      />
    );
    expect(wrapper).toMatchSnapshot();
  });

  it("Renders adding toolbar", () => {
    const wrapper = shallow(
      <AppTableWithControls
        count={120}
        items={["first", "second", "third"]}
        itemsToRow={itemsToRow}
        pagination={{ page: 1, perPage: 10 }}
        handleFilterTextChange={jest.fn()}
        handlePaginationChange={jest.fn()}
        handleSortChange={jest.fn()}
        columns={columns}
        isLoading={false}
        toolbar={<p>This is an additional content to toolbar</p>}
      />
    );
    expect(wrapper).toMatchSnapshot();
  });
});
