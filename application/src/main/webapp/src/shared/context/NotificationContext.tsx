import React, {
  useCallback,
  useContext,
  useMemo,
  useRef,
  useState,
} from "react";
import {
  Alert,
  AlertActionCloseButton,
  AlertGroup,
  AlertProps,
} from "@patternfly/react-core";

export type NotificationVariant = "success" | "danger" | "warning" | "info";

export interface Notification {
  variant: NotificationVariant;
  title: string;
  message?: React.ReactNode;
  /** Milliseconds before the toast is dismissed. Defaults to 8 seconds; `false` keeps it open. */
  timeout?: number | false;
}

interface NotificationEntry extends Notification {
  key: number;
}

interface INotificationContext {
  pushNotification: (notification: Notification) => void;
}

const NotificationContext = React.createContext<INotificationContext>({
  pushNotification: () => undefined,
});

const DEFAULT_TIMEOUT = 8000;

interface INotificationContextProviderProps {
  children: React.ReactNode;
}

/**
 * Minimal toast notifications built on top of PatternFly's AlertGroup.
 * Replaces @redhat-cloud-services/frontend-components-notifications (+ redux).
 */
export const NotificationContextProvider: React.FC<
  INotificationContextProviderProps
> = ({ children }) => {
  const [notifications, setNotifications] = useState<NotificationEntry[]>([]);
  const nextKey = useRef(0);

  const removeNotification = useCallback((key: number) => {
    setNotifications((current) => current.filter((n) => n.key !== key));
  }, []);

  const pushNotification = useCallback((notification: Notification) => {
    const key = nextKey.current++;
    setNotifications((current) => [...current, { ...notification, key }]);
  }, []);

  const contextValue = useMemo(
    () => ({ pushNotification }),
    [pushNotification]
  );

  return (
    <NotificationContext.Provider value={contextValue}>
      {children}
      <AlertGroup isToast isLiveRegion>
        {notifications.map(({ key, variant, title, message, timeout }) => {
          const alertTimeout: AlertProps["timeout"] =
            timeout === false ? false : (timeout ?? DEFAULT_TIMEOUT);
          return (
            <Alert
              key={key}
              variant={variant}
              title={title}
              timeout={alertTimeout}
              onTimeout={() => removeNotification(key)}
              actionClose={
                <AlertActionCloseButton
                  title={title}
                  onClose={() => removeNotification(key)}
                />
              }
            >
              {message}
            </Alert>
          );
        })}
      </AlertGroup>
    </NotificationContext.Provider>
  );
};

export const useNotificationContext = (): INotificationContext =>
  useContext(NotificationContext);
