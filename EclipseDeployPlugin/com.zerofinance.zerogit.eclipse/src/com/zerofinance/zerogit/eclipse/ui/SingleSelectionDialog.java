package com.zerofinance.zerogit.eclipse.ui;

import java.util.Collections;
import java.util.List;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.ListViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

final class SingleSelectionDialog extends Dialog {
    private final String title;
    private final String message;
    private final List<String> values;
    private final String manualInputMessage;
    private ListViewer viewer;
    private Text manualInput;
    private List<String> selected = Collections.emptyList();

    SingleSelectionDialog(Shell parentShell, String title, String message, List<String> values, String manualInputMessage) {
        super(parentShell);
        this.title = title;
        this.message = message;
        this.values = values;
        this.manualInputMessage = manualInputMessage;
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        area.setLayout(new GridLayout(1, false));
        Label messageLabel = new Label(area, SWT.WRAP);
        messageLabel.setText(message);
        messageLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        viewer = new ListViewer(area, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL | SWT.H_SCROLL);
        viewer.setContentProvider(ArrayContentProvider.getInstance());
        viewer.setLabelProvider(new LabelProvider());
        viewer.setInput(values);
        if (!values.isEmpty()) {
            viewer.getList().select(0);
        }
        GridData listData = new GridData(SWT.FILL, SWT.FILL, true, true);
        listData.widthHint = 480;
        listData.heightHint = 260;
        viewer.getList().setLayoutData(listData);
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
        int index = viewer == null ? -1 : viewer.getList().getSelectionIndex();
        selected = index < 0 ? Collections.<String>emptyList() : Collections.singletonList(values.get(index));
        super.okPressed();
    }

    List<String> getSelected() {
        return selected;
    }

    String getManualInput() {
        return manualInput == null ? "" : manualInput.getText().trim();
    }
}
