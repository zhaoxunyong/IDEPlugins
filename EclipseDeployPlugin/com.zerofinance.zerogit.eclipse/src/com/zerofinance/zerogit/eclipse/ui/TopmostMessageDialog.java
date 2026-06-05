package com.zerofinance.zerogit.eclipse.ui;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;

final class TopmostMessageDialog extends MessageDialog {
    TopmostMessageDialog(
            Shell parentShell,
            String dialogTitle,
            String dialogMessage,
            int dialogImageType,
            String[] dialogButtonLabels,
            int defaultIndex) {
        super(parentShell, dialogTitle, null, dialogMessage, dialogImageType, dialogButtonLabels, defaultIndex);
        setShellStyle(TopmostDialogSupport.topmostShellStyle(getShellStyle()));
    }

    static TopmostMessageDialog confirm(Shell parentShell, String dialogTitle, String dialogMessage) {
        return new TopmostMessageDialog(
                parentShell,
                dialogTitle,
                dialogMessage,
                MessageDialog.QUESTION,
                new String[] {IDialogConstants.OK_LABEL, IDialogConstants.CANCEL_LABEL},
                0);
    }

    static TopmostMessageDialog information(Shell parentShell, String dialogTitle, String dialogMessage) {
        return new TopmostMessageDialog(
                parentShell,
                dialogTitle,
                dialogMessage,
                MessageDialog.INFORMATION,
                new String[] {IDialogConstants.OK_LABEL},
                0);
    }

    static TopmostMessageDialog warning(Shell parentShell, String dialogTitle, String dialogMessage) {
        return new TopmostMessageDialog(
                parentShell,
                dialogTitle,
                dialogMessage,
                MessageDialog.WARNING,
                new String[] {IDialogConstants.OK_LABEL},
                0);
    }

    static TopmostMessageDialog error(Shell parentShell, String dialogTitle, String dialogMessage) {
        return new TopmostMessageDialog(
                parentShell,
                dialogTitle,
                dialogMessage,
                MessageDialog.ERROR,
                new String[] {IDialogConstants.OK_LABEL},
                0);
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        TopmostDialogSupport.activate(shell);
    }
}
