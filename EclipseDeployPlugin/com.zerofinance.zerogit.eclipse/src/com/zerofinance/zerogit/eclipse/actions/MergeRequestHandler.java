package com.zerofinance.zerogit.eclipse.actions;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;

import com.zerofinance.zerogit.eclipse.settings.ZeroGitSettings;

public class MergeRequestHandler extends AbstractZeroGitHandler {
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IProject project = requireProject(event);
        String repoRoot = requireRepositoryRoot(event);
        String group = requireGroupSelection(shell(event));
        String assignee = flowService().normalizeAssignee(ui().promptAssignee(shell(event), ZeroGitSettings.getGitMrAssignees()));
        if (assignee == null) {
            ui().showError(shell(event), "ZeroGit: Merge Request", "请选择 assignee，或手动填写其他 assignee 后再发起 Merge Request。");
            return null;
        }

        runScriptJob(
                shell(event),
                "Merge Request",
                project,
                buildRequest(repoRoot, "GitMergeRequest.sh", flowService().buildMergeRequestArgs(group, assignee)),
                true);
        return null;
    }
}
