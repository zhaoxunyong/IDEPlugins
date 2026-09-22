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
import org.eclipse.swt.widgets.Text;

final class CheckboxSelectionDialog extends Dialog {
    private final String title;
    private final String message;
    private final List<String> values;
    private final String manualInputMessage;
    private final boolean selectAllByDefault;
    private CheckboxTableViewer viewer;
    private Text manualInput;
    private List<String> selected = Collections.emptyList();

    CheckboxSelectionDialog(Shell parentShell, String title, String message, List<String> values) {
        this(parentShell, title, message, values, null, true);
    }

    CheckboxSelectionDialog(Shell parentShell, String title, String message, List<String> values, String manualInputMessage) {
        this(parentShell, title, message, values, manualInputMessage, true);
    }

    CheckboxSelectionDialog(Shell parentShell, String title, String message, List<String> values, String manualInputMessage, boolean selectAllByDefault) {
        super(parentShell);
        this.title = title;
        this.message = message;
        this.values = values;
        this.manualInputMessage = manualInputMessage;
        this.selectAllByDefault = selectAllByDefault;
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
        viewer.setAllChecked(selectAllByDefault);
        if (!selectAllByDefault && !values.isEmpty()) {
            viewer.setChecked(values.get(0), true);
        }
        GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true);
        tableData.widthHint = 480;
        tableData.heightHint = 300;
        viewer.getTable().setLayoutData(tableData);
        if (manualInputMessage != null) {
            Label manualInputLabel = new Label(area, SWT.WRAP);
            manualInputLabel.setText(manualInputMessage);
            manualInputLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
            manualInput = new Text(area, SWT.BORDER);
            manualInput.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        }
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

    String getManualInput() {
        return manualInput == null ? "" : manualInput.getText().trim();
    }
}
