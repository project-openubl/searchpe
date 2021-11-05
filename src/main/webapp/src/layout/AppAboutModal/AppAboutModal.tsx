import React from "react";
import {
  AboutModal,
  Flex,
  FlexItem,
  List,
  ListItem,
  TextContent,
} from "@patternfly/react-core";
import {
  GithubIcon,
  GlobeIcon,
  EnvelopeIcon,
  InfoAltIcon,
} from "@patternfly/react-icons";

import brandImage from "images/logo-navbar.svg";

export interface AppAboutModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const AppAboutModal: React.FC<AppAboutModalProps> = ({
  isOpen,
  onClose,
}) => {
  return (
    <AboutModal
      isOpen={isOpen}
      onClose={onClose}
      trademark="COPYRIGHT © 2020."
      brandImageSrc={brandImage}
      brandImageAlt="Logo"
      productName=""
    >
      <TextContent className="pf-u-py-xl">
        <h4>About</h4>
        <p>
          <a
            href="https://project-openubl.github.io/"
            target="_blank"
            rel="noopener noreferrer"
          >
            Searchpe
          </a>{" "}
          allows you to consume the data exposed from SUNAT through the 'padrón
          reducido' available online.
        </p>
      </TextContent>
      <TextContent>
        <h4>Links</h4>
        <List>
          <ListItem>
            <a
              href="https://project-openubl.github.io/"
              target="_blank"
              rel="noopener noreferrer"
            >
              Website
            </a>
          </ListItem>
          <ListItem>
            <a
              href="https://project-openubl.github.io/"
              target="_blank"
              rel="noopener noreferrer"
            >
              Documentation
            </a>
          </ListItem>
        </List>
      </TextContent>
      <div className="pf-c-about-modal-box__strapline">
        <Flex>
          <FlexItem>
            <a
              href="https://github.com/project-openubl/searchpe"
              rel="noopener noreferrer"
              target="_blank"
            >
              <i>
                <GithubIcon />
              </i>{" "}
              Source
            </a>
          </FlexItem>
          <FlexItem>
            <a
              href="https://projectopenubl.zulipchat.com/"
              rel="noopener noreferrer"
              target="_blank"
            >
              <i>
                <GlobeIcon />
              </i>{" "}
              Discussion forum
            </a>
          </FlexItem>
          <FlexItem>
            <a href="mailto:projectopenubl+subscribe@googlegroups.com?subject=Subscribe&body=Hello">
              <i>
                <EnvelopeIcon />
              </i>{" "}
              Mailing list
            </a>
          </FlexItem>
          <FlexItem>
            <a
              href="https://github.com/project-openubl/searchpe/issues"
              rel="noopener noreferrer"
              target="_blank"
            >
              <i>
                <InfoAltIcon />
              </i>{" "}
              Issue tracking
            </a>
          </FlexItem>
        </Flex>
      </div>
    </AboutModal>
  );
};
