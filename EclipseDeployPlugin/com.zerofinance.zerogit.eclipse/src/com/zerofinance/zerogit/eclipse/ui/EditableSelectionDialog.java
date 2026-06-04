package com.zerofinance.zerogit.eclipse.ui;

import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

final class EditableSelectionDialog extends Dialog {
    private final String title;
    private final String message;
    private final List<String> values;
    private final String defaultValue;

    private Combo combo;
    private String selection;

    EditableSelectionDialog(Shell parentShell, String title, String message, List<String> values, String defaultValue) {
        super(parentShell);
        this.title = StringUtils.defaultString(title);
        this.message = StringUtils.defaultString(message);
        this.values = values == null ? Collections.<String>emptyList() : values;
        this.defaultValue = StringUtils.defaultString(defaultValue);
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText(title);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        Composite container = new Composite(area, SWT.NONE);
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        container.setLayout(new GridLayout(1, false));

        if (StringUtils.isNotBlank(message)) {
            Label messageLabel = new Label(container, SWT.WRAP);
            messageLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            messageLabel.setText(message);
        }

        combo = new Combo(container, SWT.DROP_DOWN);
        combo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        combo.setItems(values.toArray(new String[0]));
        if (StringUtils.isNotBlank(defaultValue)) {
            combo.setText(defaultValue);
            int index = values.indexOf(defaultValue);
            if (index >= 0) {
                combo.select(index);
            }
        }

        return area;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
        createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
    }

    @Override
    protected void okPressed() {
        selection = combo == null ? null : combo.getText();
        super.okPressed();
    }

    String getSelection() {
        return selection;
    }
}
