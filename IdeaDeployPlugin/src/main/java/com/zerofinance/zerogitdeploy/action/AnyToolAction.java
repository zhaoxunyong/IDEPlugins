package com.zerofinance.zerogitdeploy.action;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.zerofinance.zerogitdeploy.handler.DeployPluginHandler;
import com.zerofinance.zerogitdeploy.tools.MessagesUtils;
import org.jetbrains.annotations.NotNull;

public class AnyToolAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent event) {
        Project project = event.getProject();
        try {
            DeployPluginHandler handler = new DeployPluginHandler(event);
            handler.anyTool();
        } catch (Exception e) {
            e.printStackTrace();
            MessagesUtils.showMessage(project, e.getMessage(), "Error:", NotificationType.ERROR);
        }
    }

    @Override
    public void update(@NotNull final AnActionEvent event) {
        boolean visibility = event.getProject() != null;
        event.getPresentation().setEnabled(visibility);
        event.getPresentation().setVisible(visibility);
    }
}
