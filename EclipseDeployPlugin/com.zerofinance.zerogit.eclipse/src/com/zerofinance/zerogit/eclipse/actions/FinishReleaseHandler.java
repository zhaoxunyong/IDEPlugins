package com.zerofinance.zerogit.eclipse.actions;

import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.swt.widgets.Shell;

import com.zerofinance.zerogit.eclipse.exec.CommandResult;
import com.zerofinance.zerogit.eclipse.flow.FinishReleaseOutputParser;

public class FinishReleaseHandler extends AbstractZeroGitHandler {
    private final FinishReleaseOutputParser outputParser = new FinishReleaseOutputParser();

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IProject project = requireProject(event);
        String repoRoot = requireRepositoryRoot(event);

        if (!ui().confirm(
                shell(event),
                "ZeroGit: Finish Release",
                "只有 Maintainer 才有权限，请确认你有 Maintainer 权限？\n\n"
                        + "此功能仅限于解决CICD自动化merge代码时出现冲突的场景。解决完冲突后，再到项目的Pipeline里面重新执行对应的job即可。")) {
            return null;
        }
        if (!ui().confirm(shell(event), "ZeroGit: Finish Release", "运维是否已完成上线？")) {
            return null;
        }

        List<String> releases = listAllReleaseBranches(repoRoot);
        if (releases.isEmpty()) {
            ui().showError(shell(event), "ZeroGit: Finish Release", "No release branch found.");
            return null;
        }

        String selected = ui().chooseBranch(
                shell(event),
                "ZeroGit: Finish Release",
                "请选择要结束的 release 分支",
                releases);
        if (selected == null) {
            return null;
        }

        runScriptJob(
                shell(event),
                "Finish Release",
                project,
                buildRequest(repoRoot, "FinishRelease.sh", flowService().buildFinishReleaseArgs(selected)),
                true,
                new SuccessCallback() {
                    @Override
                    public void onSuccess(Shell shell, String title, CommandResult result) {
                        showFinishResult(shell, title, result);
                    }
                });
        return null;
    }

    private List<String> listAllReleaseBranches(String repoRoot) throws ExecutionException {
        try {
            return gitRepositoryService().listAllReleaseBranches(repoRoot, true);
        } catch (Exception e) {
            throw new ExecutionException("Failed to list release branches.", e);
        }
    }

    private void showFinishResult(Shell shell, String title, CommandResult result) {
        List<String> remaining = outputParser.parseRemainingBranches(result.getOutput());
        if (remaining.isEmpty()) {
            ui().showInfo(shell, "ZeroGit", title + " completed.");
            return;
        }
        ui().showWarning(
                shell,
                "ZeroGit",
                "目前有进行中的分支：" + StringUtils.join(remaining, " ") + "，请评估是否需要重新测试。");
    }
}
