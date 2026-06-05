package com.zerofinance.zerogit.eclipse.ui;

import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.swt.widgets.Shell;

final class TopmostInputDialog extends InputDialog {
    TopmostInputDialog(
            Shell parentShell,
            String dialogTitle,
            String dialogMessage,
            String initialValue,
            IInputValidator validator) {
        super(parentShell, dialogTitle, dialogMessage, initialValue, validator);
        setShellStyle(TopmostDialogSupport.topmostShellStyle(getShellStyle()));
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        TopmostDialogSupport.activate(shell);
    }
}
