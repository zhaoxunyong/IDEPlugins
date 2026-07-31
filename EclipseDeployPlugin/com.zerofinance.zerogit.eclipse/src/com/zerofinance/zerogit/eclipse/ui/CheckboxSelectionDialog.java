package com.zerofinance.zerogit.eclipse.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

final class CheckboxSelectionDialog extends Dialog {
    private final String title;
    private final String message;
    private final List<String> values;
    private CheckboxTableViewer viewer;
    private List<String> selected = Collections.emptyList();

    CheckboxSelectionDialog(Shell parentShell, String title, String message, List<String> values) {
        super(parentShell);
        this.title = title;
        this.message = message;
        this.values = values;
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        area.setLayout(new GridLayout(1, false));

        Label messageLabel = new Label(area, SWT.WRAP);
        messageLabel.setText(message);
        messageLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        viewer = CheckboxTableViewer.newCheckList(area, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
        viewer.setContentProvider(ArrayContentProvider.getInstance());
        viewer.setLabelProvider(new LabelProvider());
        viewer.setInput(values);
        viewer.setAllChecked(true);
        GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true);
        tableData.widthHint = 480;
        tableData.heightHint = 300;
        viewer.getTable().setLayoutData(tableData);
        return area;
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText(title);
    }

    @Override
    protected void okPressed() {
        Object[] checked = viewer == null ? new Object[0] : viewer.getCheckedElements();
        List<String> result = new ArrayList<>();
        for (Object value : checked) {
            result.add(String.valueOf(value));
        }
        selected = result;
        super.okPressed();
    }

    List<String> getSelected() {
        return selected;
    }
}
