package com.zerofinance.zerogit.eclipse.actions;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;

public class StartNewFeatureHandler extends AbstractZeroGitHandler {
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IProject project = requireProject(event);
        String repoRoot = requireRepositoryRoot(event);
        String group = requireGroupSelection(shell(event));
        String featureBranch = ui().promptFeatureBranch(shell(event), group, "feature/" + group + "/");
        if (featureBranch == null) {
            return null;
        }

        String validationMessage = flowService().validateFeatureBranchName(group, featureBranch);
        if (validationMessage != null) {
            ui().showError(shell(event), "ZeroGit: Start New Feature", validationMessage);
            return null;
        }

        runScriptJob(
                shell(event),
                "Start New Feature",
                project,
                buildRequest(repoRoot, "StartNewFeature.sh", flowService().buildStartNewFeatureArgs(group, featureBranch)),
                true);
        return null;
    }
}
