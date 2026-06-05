package com.zerofinance.zerogit.eclipse.ui;

import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;

final class TopmostElementListSelectionDialog extends ElementListSelectionDialog {
    TopmostElementListSelectionDialog(Shell parent, ILabelProvider renderer) {
        super(parent, renderer);
        setShellStyle(TopmostDialogSupport.topmostShellStyle(getShellStyle()));
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        TopmostDialogSupport.activate(shell);
    }
}
