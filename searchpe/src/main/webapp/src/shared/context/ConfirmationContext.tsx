import React, { useCallback, useContext, useMemo, useReducer } from "react";
import {
  Button,
  ButtonVariant,
  Modal,
  ModalBody,
  ModalFooter,
  ModalHeader,
  ModalHeaderProps,
} from "@patternfly/react-core";

// Inlined (and ported to PatternFly 6) from @project-openubl/lib-ui

export interface ConfirmationOptions {
  title: string;
  titleIconVariant?: ModalHeaderProps["titleIconVariant"];
  message: React.ReactNode;
  confirmBtnLabel: string;
  cancelBtnLabel: string;
  confirmBtnVariant: ButtonVariant;
  onConfirm: () => void;
}

interface IConfirmationContext {
  open: (options: ConfirmationOptions) => void;
  enableProcessing: () => void;
  close: () => void;
}

const ConfirmationContext = React.createContext<IConfirmationContext>({
  open: () => undefined,
  enableProcessing: () => undefined,
  close: () => undefined,
});

type State = Readonly<
  ConfirmationOptions & {
    isOpen: boolean;
    isProcessing: boolean;
  }
>;

type Action =
  | { type: "open"; payload: ConfirmationOptions }
  | { type: "enableProcessing" }
  | { type: "close" };

const defaultState: State = {
  isOpen: false,
  isProcessing: false,

  title: "",
  titleIconVariant: undefined,
  message: "",
  confirmBtnLabel: "Confirm",
  cancelBtnLabel: "Cancel",
  confirmBtnVariant: ButtonVariant.primary,

  onConfirm: () => undefined,
};

const reducer = (state: State, action: Action): State => {
  switch (action.type) {
    case "open":
      return { ...state, ...action.payload, isOpen: true };
    case "enableProcessing":
      return { ...state, isProcessing: true };
    case "close":
      return defaultState;
    default:
      return state;
  }
};

interface IConfirmationContextProviderProps {
  children: React.ReactNode;
}

export const ConfirmationContextProvider: React.FC<
  IConfirmationContextProviderProps
> = ({ children }) => {
  const [state, dispatch] = useReducer(reducer, defaultState);

  const open = useCallback(
    (options: ConfirmationOptions) =>
      dispatch({ type: "open", payload: options }),
    []
  );
  const enableProcessing = useCallback(
    () => dispatch({ type: "enableProcessing" }),
    []
  );
  const close = useCallback(() => dispatch({ type: "close" }), []);

  const contextValue = useMemo(
    () => ({ open, enableProcessing, close }),
    [open, enableProcessing, close]
  );

  return (
    <ConfirmationContext.Provider value={contextValue}>
      {children}
      <Modal
        variant="small"
        isOpen={state.isOpen}
        onClose={close}
        aria-labelledby="confirm-dialog-title"
        aria-label="confirm-dialog"
      >
        <ModalHeader
          title={state.title}
          titleIconVariant={state.titleIconVariant}
          labelId="confirm-dialog-title"
        />
        <ModalBody>{state.message}</ModalBody>
        <ModalFooter>
          <Button
            key="confirm"
            aria-label="confirm"
            variant={state.confirmBtnVariant}
            isDisabled={state.isProcessing}
            onClick={state.onConfirm}
          >
            {state.confirmBtnLabel}
          </Button>
          <Button
            key="cancel"
            aria-label="cancel"
            variant={ButtonVariant.link}
            isDisabled={state.isProcessing}
            onClick={close}
          >
            {state.cancelBtnLabel}
          </Button>
        </ModalFooter>
      </Modal>
    </ConfirmationContext.Provider>
  );
};

export const useConfirmationContext = (): IConfirmationContext =>
  useContext(ConfirmationContext);
