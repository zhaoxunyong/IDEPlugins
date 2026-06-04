package com.zerofinance.zerogit.eclipse.actions;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;

public class GitFlowGuidelineHandler extends AbstractZeroGitHandler {
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        boolean opened = ui().openExternalUrl(flowService().GITFLOW_GUIDELINE_URL);
        if (!opened) {
            ui().showError(shell(event), "ZeroGit: GitFlow Guideline", "无法打开 GitFlow Guideline 链接。");
        }
        return null;
    }
}
