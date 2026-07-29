package com.zerofinance.zerogit.eclipse.actions;

import java.util.Collections;

import org.apache.commons.lang.StringUtils;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;

public class AiCodeReviewHandler extends AbstractZeroGitHandler {
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IProject project = requireProject(event);
        String repoRoot = requireRepositoryRoot(event);
        String commitRange = StringUtils.trimToEmpty(ui().promptText(
                shell(event),
                "ZeroGit: AI Code Review",
                "输入单个commit时只评审该提交；输入commit范围时评审该范围内的提交。留空则评审已暂存变更。\n"
                        + "例如：HEAD（单提交）、HEAD~3（最近 3 个提交）、a b（a-b区间的提交）",
                ""));
        if (StringUtils.isBlank(commitRange) && !hasStagedChanges(repoRoot)) {
            ui().showWarning(shell(event), "ZeroGit: AI Code Review", "请先执行 git add 后再运行 AI Code Review");
            return null;
        }
        runScriptJob(
                shell(event),
                "AI Code Review",
                project,
                buildRequest(repoRoot, "AiCodeReview.sh", StringUtils.isBlank(commitRange)
                        ? Collections.<String>emptyList()
                        : Collections.singletonList(commitRange)),
                false);
        return null;
    }
}
