package com.zerofinance.zerogit.eclipse.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;

final class TopmostDialogSupport {
    private TopmostDialogSupport() {
    }

    static int topmostShellStyle(int shellStyle) {
        return shellStyle | SWT.ON_TOP | SWT.APPLICATION_MODAL;
    }

    static void activate(Shell shell) {
        if (shell == null || shell.isDisposed()) {
            return;
        }
        shell.setActive();
        shell.forceActive();
    }
}
