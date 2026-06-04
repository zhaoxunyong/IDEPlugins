package com.zerofinance.zerogit.eclipse.ui;

import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;
import org.eclipse.jface.viewers.LabelProvider;

public class UserInteraction {

    public String chooseGroup(Shell shell, List<String> groups, String defaultGroup) {
        return chooseFromList(
                shell,
                "ZeroGit: Select Group",
                "请选择 ZeroGit Group",
                groups,
                defaultGroup);
    }

    public String chooseBranch(Shell shell, String title, String message, List<String> branches) {
        return chooseFromList(shell, title, message, branches, null);
    }

    public String promptFeatureBranch(Shell shell, String group, String initialValue) {
        String prefix = "feature/" + StringUtils.trimToEmpty(group) + "/";
        InputDialog dialog = new InputDialog(
                shell,
                "ZeroGit: Start New Feature",
                "请输入 Feature 分支名（需以 " + prefix + " 开头）",
                StringUtils.defaultIfEmpty(initialValue, prefix),
                null);
        return dialog.open() == Window.OK ? StringUtils.trimToNull(dialog.getValue()) : null;
    }

    public String promptAssignee(Shell shell, List<String> assignees) {
        String defaultValue = assignees == null || assignees.isEmpty() ? "" : assignees.get(0);
        String message = "请选择 assignee，或直接输入其他 GitLab 用户名";
        if (assignees != null && !assignees.isEmpty()) {
            message += "\n已配置: " + StringUtils.join(assignees, ", ");
        }
        InputDialog dialog = new InputDialog(
                shell,
                "ZeroGit: Merge Request",
                message,
                defaultValue,
                null);
        return dialog.open() == Window.OK ? StringUtils.trimToNull(dialog.getValue()) : null;
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
