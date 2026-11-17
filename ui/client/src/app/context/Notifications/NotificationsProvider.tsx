import type React from "react";
import { useCallback, useMemo, useState } from "react";

import {
  Alert,
  AlertActionCloseButton,
  AlertGroup,
  Content,
  ContentVariants,
} from "@patternfly/react-core";

import {
  type INotification,
  NotificationsContext,
} from "./NotificationsContext";

let notificationId = 0;

interface INotificationsProviderProps {
  children: React.ReactNode;
}

export const NotificationsProvider: React.FC<INotificationsProviderProps> = ({
  children,
}) => {
  const [notifications, setNotifications] = useState<INotification[]>([]);

  const removeNotification = (id: number) => {
    setNotifications((prev) => prev.filter((n) => n.id !== id));
  };

  const addNotification = useCallback(
    (notification: Omit<INotification, "id">) => {
      const id = ++notificationId;
      setNotifications((prev) => [...prev, { ...notification, id }]);
    },
    [],
  );

  const value = useMemo(() => ({ addNotification }), [addNotification]);

  return (
    <NotificationsContext value={value}>
      {children}
      <AlertGroup isToast isLiveRegion>
        {notifications.map((n) => (
          <Alert
            key={n.id}
            variant={n.variant}
            title={n.title}
            timeout={8000}
            onTimeout={() => removeNotification(n.id)}
            actionClose={
              <AlertActionCloseButton
                onClose={() => removeNotification(n.id)}
              />
            }
          >
            {n.description}
            {n.detail ? (
              <Content component={ContentVariants.small}>{n.detail}</Content>
            ) : null}
          </Alert>
        ))}
      </AlertGroup>
    </NotificationsContext>
  );
};
