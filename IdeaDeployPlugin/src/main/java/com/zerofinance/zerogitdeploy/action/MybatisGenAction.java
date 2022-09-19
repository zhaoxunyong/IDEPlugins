package com.zerofinance.zerogitdeploy.action;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.zerofinance.zerogitdeploy.handler.DeployPluginHandler;
import com.zerofinance.zerogitdeploy.tools.MessagesUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class MybatisGenAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent event) {
        Project project = event.getProject();
        try {
            DeployPluginHandler handler = new DeployPluginHandler(event);
            handler.mybatisGen();
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
