import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import {
  Notification,
  NotificationContextProvider,
  useNotificationContext,
} from "./NotificationContext";

const Pusher: React.FC<{ notification: Notification }> = ({ notification }) => {
  const { pushNotification } = useNotificationContext();
  return (
    <button type="button" onClick={() => pushNotification(notification)}>
      push
    </button>
  );
};

describe("NotificationContext", () => {
  it("pushNotification renders a toast alert", async () => {
    const user = userEvent.setup();
    render(
      <NotificationContextProvider>
        <Pusher
          notification={{
            variant: "danger",
            title: "my title",
            message: "my message",
          }}
        />
      </NotificationContextProvider>
    );

    await user.click(screen.getByRole("button", { name: "push" }));

    expect(screen.getByText("my title")).toBeInTheDocument();
    expect(screen.getByText("my message")).toBeInTheDocument();
    expect(screen.getByText("my title").closest(".pf-v6-c-alert")).toHaveClass(
      "pf-m-danger"
    );
  });

  it("notification can be closed", async () => {
    const user = userEvent.setup();
    render(
      <NotificationContextProvider>
        <Pusher notification={{ variant: "success", title: "Éxito" }} />
      </NotificationContextProvider>
    );

    await user.click(screen.getByRole("button", { name: "push" }));
    expect(screen.getByText("Éxito")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /Close/ }));
    expect(screen.queryByText("Éxito")).not.toBeInTheDocument();
  });

  it("notification is dismissed after its timeout", () => {
    vi.useFakeTimers();
    try {
      render(
        <NotificationContextProvider>
          <Pusher
            notification={{ variant: "info", title: "Bye", timeout: 1000 }}
          />
        </NotificationContextProvider>
      );

      act(() => screen.getByRole("button", { name: "push" }).click());
      expect(screen.getByText("Bye")).toBeInTheDocument();

      act(() => {
        vi.advanceTimersByTime(1500);
      });
      expect(screen.queryByText("Bye")).not.toBeInTheDocument();
    } finally {
      vi.useRealTimers();
    }
  });
});
