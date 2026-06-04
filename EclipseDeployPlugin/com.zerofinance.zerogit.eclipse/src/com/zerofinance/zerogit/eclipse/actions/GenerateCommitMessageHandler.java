package com.zerofinance.zerogit.eclipse.actions;

import java.util.Collections;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;

public class GenerateCommitMessageHandler extends AbstractZeroGitHandler {
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IProject project = requireProject(event);
        String repoRoot = requireRepositoryRoot(event);
        if (!hasStagedChanges(repoRoot)) {
            ui().showWarning(shell(event), "ZeroGit: Generate Commit Message", "请先执行 git add 后再生成 Commit Message");
            return null;
        }
        runScriptJob(
                shell(event),
                "Generate Commit Message",
                project,
                buildRequest(repoRoot, "GenCommitMessage.sh", Collections.<String>emptyList()),
                false);
        return null;
    }
}
