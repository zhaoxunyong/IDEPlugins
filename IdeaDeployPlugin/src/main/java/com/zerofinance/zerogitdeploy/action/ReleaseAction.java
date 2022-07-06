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
import com.zerofinance.zerogitdeploy.tools.CommandUtils;
import com.zerofinance.zerogitdeploy.handler.DeployPluginHandler;
import com.zerofinance.zerogitdeploy.tools.MessagesUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class ReleaseAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent event) {
        Project project = event.getProject();

        VirtualFile vFile = event.getData(PlatformDataKeys.VIRTUAL_FILE);
        if(vFile == null) {
            // showMessage("Please pick up a valid module!", "Error", NotificationType.ERROR);
            // Messages.showErrorDialog("Please pick up a valid module!", "Error");
            MessagesUtils.showMessage(project, "Please pick up a valid module!", "Error:", NotificationType.ERROR);
            return;
        }
//        @Nullable Module module = ModuleUtil.findModuleForFile(vFile, project);
//        String moduleRootPath = ModuleRootManager.getInstance(module).getContentRoots()[0].getPath();
//        System.out.println("module--->"+module.getName()+"/"+module.getModuleFilePath());
//        System.out.println("moduleRootPath--->"+moduleRootPath);
        String modulePath = vFile.getPath();
        String rootProjectPath = CommandUtils.getRootProjectPath(modulePath);
        String moduleName = new File(rootProjectPath).getName();
//        MessagesUtils.showMessage(project, "\""+moduleName+"\" was selected!", "Information:", NotificationType.INFORMATION);

        try {
            DeployPluginHandler handler = new DeployPluginHandler(project, modulePath, moduleName);
            if(handler.preCheck()) {
                handler.release();
            }

        } catch (Exception e) {
            e.printStackTrace();
//            Messages.showErrorDialog(e.getMessage(), "Error");
            MessagesUtils.showMessage(project, e.getMessage(), moduleName+"： Error:", NotificationType.ERROR);
        }
    }

    @Override
    public void update(@NotNull final AnActionEvent event) {
        boolean visibility = event.getProject() != null;
        event.getPresentation().setEnabled(visibility);
        event.getPresentation().setVisible(visibility);
    }
}
