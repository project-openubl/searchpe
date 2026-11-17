import type { ReactNode } from "react";
import { createContext } from "react";

export type NotificationVariant =
  "success" | "danger" | "warning" | "info" | "custom";

export interface INotification {
  id: number;
  title: string;
  variant: NotificationVariant;
  description?: ReactNode;
  /** A server error's `detail`, shown under the description (ADR 0006). */
  detail?: string;
}

export interface INotificationsContextValue {
  addNotification: (notification: Omit<INotification, "id">) => void;
}

export const NotificationsContext =
  createContext<INotificationsContextValue | null>(null);
