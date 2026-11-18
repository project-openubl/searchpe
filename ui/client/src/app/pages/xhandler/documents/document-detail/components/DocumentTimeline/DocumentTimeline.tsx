import type React from "react";

import dayjs from "dayjs";

import {
  Content,
  ContentVariants,
  ProgressStep,
  ProgressStepper,
  type ProgressStepProps,
} from "@patternfly/react-core";

import type { Event } from "@app/client/xhandler";
import { DOCUMENT_STATUS_PRESENTATION } from "@app/components/DocumentStatusLabel";
import { RENDER_DATETIME_FORMAT } from "@app/constants";
import { isDocumentStatus } from "@app/queries/xhandler/documents";

interface IDocumentTimelineProps {
  events: Event[];
}

const VARIANT_OF_STATUS: Record<string, ProgressStepProps["variant"]> = {
  accepted: "success",
  accepted_with_observations: "warning",
  rejected: "danger",
  exception: "danger",
  unknown: "warning",
  voided: "success",
};

/** The document's events, oldest first, as the service recorded them. */
export const DocumentTimeline: React.FC<IDocumentTimelineProps> = ({
  events,
}) => {
  if (events.length === 0) {
    return <Content component={ContentVariants.p}>Aún no hay eventos.</Content>;
  }
  return (
    <ProgressStepper isVertical aria-label="Línea de tiempo del comprobante">
      {events.map((event, index) => {
        const status = event.status ?? "";
        const title = isDocumentStatus(status)
          ? DOCUMENT_STATUS_PRESENTATION[status].label
          : status || (event.message ?? "Evento");
        const id = `document-event-${index}`;
        return (
          <ProgressStep
            key={id}
            id={id}
            titleId={`${id}-title`}
            variant={VARIANT_OF_STATUS[status] ?? "info"}
            description={
              <>
                {event.at
                  ? dayjs(event.at).format(RENDER_DATETIME_FORMAT)
                  : null}
                {event.code !== undefined && event.code !== null
                  ? ` · Código SUNAT ${event.code}`
                  : null}
                {event.message && event.message !== title ? (
                  <>
                    <br />
                    {event.message}
                  </>
                ) : null}
                {event.actor ? (
                  <>
                    <br />
                    Por {event.actor}
                  </>
                ) : null}
              </>
            }
          >
            {title}
          </ProgressStep>
        );
      })}
    </ProgressStepper>
  );
};
