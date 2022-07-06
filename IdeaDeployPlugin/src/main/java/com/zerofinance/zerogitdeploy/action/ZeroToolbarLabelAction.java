package com.zerofinance.zerogitdeploy.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.ToolbarLabelAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ZeroToolbarLabelAction extends ToolbarLabelAction {
    @Override
    public void update(@NotNull AnActionEvent e) {
        super.update(e);

        Project project = e.getProject();
        e.getPresentation().setText(getConsolidatedVcsName(project));
    }

    @NlsContexts.Label
    private static String getConsolidatedVcsName(@Nullable Project project) {
        return "Zero:";
    }
}
