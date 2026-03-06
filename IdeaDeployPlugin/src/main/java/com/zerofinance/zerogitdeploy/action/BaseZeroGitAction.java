package com.zerofinance.zerogitdeploy.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.zerofinance.zerogitdeploy.handler.ZeroGitFlowHandler;
import com.zerofinance.zerogitdeploy.tools.MessagesUtils;
import org.jetbrains.annotations.NotNull;

public abstract class BaseZeroGitAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent event) {
        Project project = event.getProject();
        try {
            ZeroGitFlowHandler handler = new ZeroGitFlowHandler(event);
            execute(handler);
        } catch (Exception e) {
            if (project != null) {
                String actionName = event.getPresentation().getText();
                if (actionName == null || actionName.trim().isEmpty()) {
                    actionName = "ZeroGit Action";
                }
                String detail = MessagesUtils.buildDetailedErrorMessage(e);
                String summary = e.getMessage();
                if (summary == null || summary.trim().isEmpty()) {
                    summary = "执行失败，请点击“复制完整错误”获取详细异常信息。";
                }
                MessagesUtils.showErrorWithDetails(project, actionName + " failed", summary, detail);
            }
        }
    }

    @Override
    public void update(@NotNull final AnActionEvent event) {
        boolean visibility = event.getProject() != null;
        event.getPresentation().setEnabled(visibility);
        event.getPresentation().setVisible(visibility);
    }

    protected abstract void execute(ZeroGitFlowHandler handler) throws Exception;
}
