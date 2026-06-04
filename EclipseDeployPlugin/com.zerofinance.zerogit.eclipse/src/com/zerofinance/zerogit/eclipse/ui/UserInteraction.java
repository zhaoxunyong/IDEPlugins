package com.zerofinance.zerogit.eclipse.ui;

import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;

public class UserInteraction {

    public String chooseGroup(Shell shell, List<String> groups, String defaultGroup) {
        return chooseValue(
                shell,
                "ZeroGit: Select Group",
                "\u8bf7\u9009\u62e9 ZeroGit Group",
                groups,
                defaultGroup);
    }

    public String chooseBranch(Shell shell, String title, String message, List<String> branches) {
        return chooseValue(shell, title, message, branches, null);
    }

    public String chooseValue(Shell shell, String title, String message, List<String> values, String defaultValue) {
        return chooseFromList(shell, title, message, values, defaultValue);
    }

    public String promptFeatureBranch(Shell shell, String group, String initialValue) {
        String prefix = "feature/" + StringUtils.trimToEmpty(group) + "/";
        return openTextInputDialog(
                shell,
                "ZeroGit: Start New Feature",
                "\u8bf7\u8f93\u5165 Feature \u5206\u652f\u540d\uff08\u9700\u4ee5 " + prefix + " \u5f00\u5934\uff09",
                StringUtils.defaultIfEmpty(initialValue, prefix));
    }

    public String promptAssignee(Shell shell, List<String> assignees) {
        List<String> candidateValues = assignees == null ? java.util.Collections.<String>emptyList() : assignees;
        String defaultValue = candidateValues.isEmpty() ? "" : candidateValues.get(0);
        String message = "\u8bf7\u9009\u62e9 assignee\uff0c\u6216\u76f4\u63a5\u8f93\u5165\u5176\u4ed6 GitLab \u7528\u6237\u540d";
        if (!candidateValues.isEmpty()) {
            message += "\n\u5df2\u914d\u7f6e: " + StringUtils.join(candidateValues, ", ");
        }
        return openEditableSelectionDialog(shell, "ZeroGit: Merge Request", message, candidateValues, defaultValue);
    }

    public String promptText(Shell shell, String title, String message, String initialValue) {
        return openTextInputDialog(shell, title, message, StringUtils.defaultString(initialValue));
    }

    public boolean confirm(Shell shell, String title, String message) {
        return MessageDialog.openConfirm(shell, title, message);
    }

    public void showInfo(Shell shell, String title, String message) {
        MessageDialog.openInformation(shell, title, message);
    }

    public void showWarning(Shell shell, String title, String message) {
        MessageDialog.openWarning(shell, title, message);
    }

    public void showError(Shell shell, String title, String message) {
        MessageDialog.openError(shell, title, message);
    }

    public boolean openExternalUrl(String url) {
        return Program.launch(StringUtils.trimToEmpty(url));
    }

    protected String openTextInputDialog(Shell shell, String title, String message, String initialValue) {
        InputDialog dialog = new InputDialog(shell, title, message, StringUtils.defaultString(initialValue), null);
        return dialog.open() == Window.OK ? StringUtils.trimToNull(dialog.getValue()) : null;
    }

    protected String openEditableSelectionDialog(
            Shell shell,
            String title,
            String message,
            List<String> values,
            String defaultValue) {
        EditableSelectionDialog dialog = new EditableSelectionDialog(shell, title, message, values, defaultValue);
        return dialog.open() == Window.OK ? StringUtils.trimToNull(dialog.getSelection()) : null;
    }

    private String chooseFromList(Shell shell, String title, String message, List<String> values, String defaultValue) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        ElementListSelectionDialog dialog = new ElementListSelectionDialog(shell, new LabelProvider());
        dialog.setTitle(title);
        dialog.setMessage(message);
        dialog.setElements(values.toArray(new String[0]));
        if (StringUtils.isNotBlank(defaultValue)) {
            dialog.setInitialSelections(new Object[] {defaultValue});
        }
        if (dialog.open() != Window.OK) {
            return null;
        }
        Object selected = dialog.getFirstResult();
        return selected == null ? null : String.valueOf(selected);
    }
}
