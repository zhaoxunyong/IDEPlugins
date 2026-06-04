package com.zerofinance.zerogit.eclipse.actions;

import java.util.List;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;

public class FinishFeatureHandler extends AbstractZeroGitHandler {
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IProject project = requireProject(event);
        String repoRoot = requireRepositoryRoot(event);
        String group = requireGroupSelection(shell(event));

        if (!ui().confirm(
                shell(event),
                "ZeroGit: Finish Feature",
                "是否已在gitlab中MR到develop-" + group + "，并完成了Merge操作？继续流程只会删除本地的feature分支。")) {
            return null;
        }

        List<String> branches;
        try {
            branches = flowService().sortFeatureBranches(gitRepositoryService().listLocalFeatureBranches(repoRoot, group));
        } catch (Exception e) {
            throw new ExecutionException("Failed to list local feature branches.", e);
        }
        if (branches.isEmpty()) {
            ui().showError(shell(event), "ZeroGit: Finish Feature", "No local feature branch found for group \"" + group + "\".");
            return null;
        }

        String selected = ui().chooseBranch(
                shell(event),
                "ZeroGit: Finish Feature",
                "请选择要结束的 feature 分支",
                branches);
        if (selected == null) {
            return null;
        }

        runScriptJob(
                shell(event),
                "Finish Feature",
                project,
                buildRequest(repoRoot, "FinishFeature.sh", flowService().buildFinishFeatureArgs(group, selected)),
                true);
        return null;
    }
}
