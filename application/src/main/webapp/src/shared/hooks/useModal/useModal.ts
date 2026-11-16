import { useCallback, useState } from "react";

// Inlined from @project-openubl/lib-ui

interface State<A, T> {
  action?: A;
  data?: T;
  isOpen: boolean;
}

interface HookState<A, T> extends State<A, T> {
  open: (action: A, data?: T) => void;
  close: () => void;
  isAction: (action: A) => boolean;
}

export const useModal = <A, T = any>(): HookState<A, T> => {
  const [state, setState] = useState<State<A, T>>({ isOpen: false });

  const open = useCallback((action: A, data?: T) => {
    setState({ action, data, isOpen: true });
  }, []);

  const close = useCallback(() => {
    setState({ isOpen: false });
  }, []);

  return {
    action: state.action,
    data: state.data,
    isOpen: state.isOpen,
    open,
    close,
    isAction: (action: A) => state.action === action,
  };
};

export default useModal;
