import React from "react";
import {
  matchPath,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from "react-router-dom";

import {
  PageSection,
  PageSectionVariants,
  Tab,
  Tabs,
  TabTitleText,
} from "@patternfly/react-core";

import { SimplePageSection } from "shared/components";

import { Overview } from "./overview";
import { PasswordAndAuthentication } from "./password-and-authentication";

enum TabType {
  overview = "overview",
  passwordAndAuthentication = "passwordAndAuthentication",
}
type TabListType = {
  [key in TabType]: {
    path: string;
  };
};
const tabList: TabListType = {
  overview: {
    path: "/profile",
  },
  passwordAndAuthentication: {
    path: "/profile/security",
  },
};

export const Profile: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();

  const isOverview = matchPath(tabList.overview.path, location.pathname);
  const isPasswordAndAuthentication = matchPath(
    tabList.passwordAndAuthentication.path,
    location.pathname
  );

  let activeKey;
  if (isOverview) {
    activeKey = TabType.overview;
  } else if (isPasswordAndAuthentication) {
    activeKey = TabType.passwordAndAuthentication;
  }

  return (
    <>
      <SimplePageSection title="Profile" description="Your personal account" />
      <PageSection variant={PageSectionVariants.light} type="tabs">
        <Tabs
          activeKey={activeKey}
          onSelect={(_event, eventKey) => {
            navigate(tabList[eventKey as TabType].path);
          }}
        >
          <Tab
            eventKey={TabType.overview}
            title={<TabTitleText>Overview</TabTitleText>}
          />
          <Tab
            eventKey={TabType.passwordAndAuthentication}
            title={<TabTitleText>Password and authentication</TabTitleText>}
          />
        </Tabs>
      </PageSection>
      <PageSection>
        <Routes>
          <Route path="/" element={<Overview />} />
          <Route path="/security" element={<PasswordAndAuthentication />} />
        </Routes>
      </PageSection>
    </>
  );
};
