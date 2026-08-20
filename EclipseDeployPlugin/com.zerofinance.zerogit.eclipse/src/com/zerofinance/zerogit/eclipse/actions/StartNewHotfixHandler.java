package com.zerofinance.zerogit.eclipse.actions;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;

import com.zerofinance.zerogit.eclipse.exec.CommandResult;

public class StartNewHotfixHandler extends AbstractZeroGitHandler {
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IProject project = requireProject(event);
        String repoRoot = requireRepositoryRoot(event);
        String group = requireGroupSelection(shell(event));

        if (!ui().confirm(
                shell(event),
                "ZeroGit: Start New Hotfix",
                "请确认上线后是否有及时合并代码到 main/develop/release/hotfix 分支？hotfix会基于最新的生产环境tag来创建。")) {
            return null;
        }
        if (!confirmPomSnapshotIfPresent(shell(event), repoRoot)) {
            return null;
        }

        CommandResult branchResult = runScriptNow(buildRequest(
                repoRoot, "GetHotfixBranch.sh", java.util.Collections.singletonList(group)));
        if (!branchResult.isSuccess()) {
            throw new ExecutionException("GetHotfixBranch.sh failed: " + branchResult.getOutput());
        }
        Map<String, String> hotfixInfo = parseHotfixBranchOutput(branchResult.getOutput());
        String suggestedBranch = hotfixInfo.get("hotfixName");
        String baseTag = hotfixInfo.get("baseTag");
        String branchName = ui().promptText(
                shell(event),
                "ZeroGit: Start New Hotfix",
                "请输入 Hotfix 分支（SemVer）\n"
                        + "1. 最新的 tag：" + baseTag + "\n"
                        + "2. 最新的 release：" + valueOrNone(hotfixInfo.get("latestReleaseBranch")) + "\n"
                        + "3. 最新的 hotfix：" + valueOrNone(hotfixInfo.get("latestHotfixBranch")) + "\n"
                        + "建议 hotfix 版本：" + suggestedBranch.substring(suggestedBranch.lastIndexOf('/') + 1) + "。请输入 hotfix 版本。",
                suggestedBranch);
        if (branchName == null) {
            return null;
        }
        String validationMessage = flowService().validateHotfixBranchName(group, branchName);
        if (validationMessage != null) {
            ui().showError(shell(event), "ZeroGit: Start New Hotfix", validationMessage);
            return null;
        }
        if (!ui().confirm(
                shell(event),
                "ZeroGit: Start New Hotfix",
                "即将基于生产 Tag " + baseTag + " 创建新的 hotfix：\n" + branchName + "\n\n请确认新生成的 hotfix 是否正确？")) {
            return null;
        }

        runScriptJob(
                shell(event),
                "Start New Hotfix",
                project,
                buildRequest(
                        repoRoot,
                        "StartNewHotfix.sh",
                        flowService().buildStartHotfixArgs(group, branchName, baseTag)),
                true);
        return null;
    }

    private String valueOrNone(String value) {
        return value == null || value.trim().isEmpty() ? "无" : value;
    }

    private Map<String, String> parseHotfixBranchOutput(String output) throws ExecutionException {
        Map<String, String> values = new HashMap<String, String>();
        for (String line : String.valueOf(output == null ? "" : output).split("\\R")) {
            int separator = line.indexOf('=');
            if (separator > 0) {
                values.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
            }
        }
        String hotfixName = values.get("hotfixName");
        if (hotfixName == null || !Pattern.matches("^hotfix/[^/]+/\\d+\\.\\d+\\.\\d+$", hotfixName)
                || values.get("baseTag") == null || values.get("baseTag").trim().isEmpty()) {
            throw new ExecutionException("GetHotfixBranch.sh returned an invalid result.");
        }
        return values;
    }
}
