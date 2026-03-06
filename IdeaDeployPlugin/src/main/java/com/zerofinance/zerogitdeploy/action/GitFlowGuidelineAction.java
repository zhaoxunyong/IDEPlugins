package com.zerofinance.zerogitdeploy.action;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Opens GitFlow Guideline (Feishu wiki) in the system default browser.
 */
public class GitFlowGuidelineAction extends AnAction {

    private static final String GITFLOW_GUIDELINE_URL =
            "https://v04jaasnl45.feishu.cn/wiki/Vg5PwK2smiPxGLk7w4Gc7tZanjb";

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        BrowserUtil.browse(GITFLOW_GUIDELINE_URL);
    }
}
