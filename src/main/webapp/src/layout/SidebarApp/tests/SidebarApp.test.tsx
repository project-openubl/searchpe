import React from "react";
import { shallow } from "enzyme";
import { SidebarApp } from "../SidebarApp";

it("Test snapshot", () => {
  const wrapper = shallow(<SidebarApp />);
  expect(wrapper).toMatchSnapshot();
});
