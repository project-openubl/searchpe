import React from "react";
import { Button, Modal, ButtonVariant } from "@patternfly/react-core";
import { deleteDialogActions } from "store/deleteDialog";

interface Props {
  onDelete: () => void;
  onCancel: typeof deleteDialogActions.closeModal;
  isOpen: boolean;
  isProcessing: boolean;
  isError: boolean;
  name: string;
  type: string;
  config: any;
}

interface State {}

class DeleteDialogBase extends React.Component<Props, State> {
  public render() {
    const {
      type,
      name,
      onDelete,
      onCancel,
      isOpen,
      isProcessing,
      isError,
      config,
    } = this.props;

    return (
      <Modal
        variant={"small"}
        title={config.title ? config.title : `Delete ${name}?`}
        onClose={() => {
          onCancel();
        }}
        isOpen={isOpen}
        actions={[
          <Button
            key="confirm"
            isDisabled={isProcessing}
            variant={ButtonVariant.danger}
            onClick={onDelete}
          >
            {config.deleteBtnLabel ? config.deleteBtnLabel : "Delete"}
          </Button>,
          <Button
            key="cancel"
            isDisabled={isProcessing}
            variant={ButtonVariant.link}
            onClick={() => {
              onCancel();
            }}
          >
            {config.cancelBtnLabel ? config.cancelBtnLabel : "Cancel"}
          </Button>,
        ]}
      >
        {isError
          ? `Ops! There was a problem while executing your action.`
          : config.message
          ? config.message
          : `Are you sure you want to delete this ${type}? This action will remove ${name} permanently.`}
      </Modal>
    );
  }
}

export default DeleteDialogBase;
