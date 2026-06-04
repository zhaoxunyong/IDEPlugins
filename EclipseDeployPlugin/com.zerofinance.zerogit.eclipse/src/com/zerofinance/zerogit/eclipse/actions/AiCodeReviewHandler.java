package com.zerofinance.zerogit.eclipse.actions;

import java.util.Collections;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;

public class AiCodeReviewHandler extends AbstractZeroGitHandler {
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IProject project = requireProject(event);
        String repoRoot = requireRepositoryRoot(event);
        if (!hasStagedChanges(repoRoot)) {
            ui().showWarning(shell(event), "ZeroGit: AI Code Review", "请先执行 git add 后再运行 AI Code Review");
            return null;
        }
        runScriptJob(
                shell(event),
                "AI Code Review",
                project,
                buildRequest(repoRoot, "AiCodeReview.sh", Collections.<String>emptyList()),
                false);
        return null;
    }
}
