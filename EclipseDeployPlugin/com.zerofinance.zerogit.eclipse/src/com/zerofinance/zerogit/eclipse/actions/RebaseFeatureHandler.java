package com.zerofinance.zerogit.eclipse.actions;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;

public class RebaseFeatureHandler extends AbstractZeroGitHandler {
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IProject project = requireProject(event);
        String repoRoot = requireRepositoryRoot(event);
        String group = requireGroupSelection(shell(event));

        String currentBranch;
        try {
            currentBranch = gitRepositoryService().readCurrentBranch(repoRoot);
        } catch (Exception e) {
            throw new ExecutionException("Failed to read current branch.", e);
        }

        String validationMessage = flowService().validateCurrentFeatureBranch(group, currentBranch);
        if (validationMessage != null) {
            ui().showError(shell(event), "ZeroGit: Rebase Feature", validationMessage);
            return null;
        }

        runScriptJob(
                shell(event),
                "Rebase Feature",
                project,
                buildRequest(repoRoot, "RebaseFeature.sh", flowService().buildRebaseFeatureArgs(group, currentBranch)),
                false);
        return null;
    }
}
