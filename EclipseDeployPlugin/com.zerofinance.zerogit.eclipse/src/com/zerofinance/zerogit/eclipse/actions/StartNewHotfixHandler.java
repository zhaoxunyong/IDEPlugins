package com.zerofinance.zerogit.eclipse.actions;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;

import com.zerofinance.zerogit.eclipse.git.VersionService;
import com.zerofinance.zerogit.eclipse.git.VersionService.HotfixBaseTagInfo;

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

        HotfixBaseTagInfo latestTag = latestHotfixBaseTag(repoRoot);
        if (latestTag == null) {
            ui().showError(
                    shell(event),
                    "ZeroGit: Start New Hotfix",
                    "未找到以 -YYYYMMDDHHmm 结尾的远程 release/hotfix tag。");
            return null;
        }

        VersionService versionService = new VersionService();
        List<String> releaseBranches = listReleaseBranches(repoRoot, group);
        List<String> hotfixBranches = listHotfixBranches(repoRoot, group);
        List<String> sameGroupBranches = new ArrayList<String>();
        sameGroupBranches.addAll(releaseBranches);
        sameGroupBranches.addAll(hotfixBranches);
        String suggestedBranch = versionService.suggestNextHotfix(latestTag.getTagName(), sameGroupBranches, group);
        String latestReleaseVersion = releaseBranches.isEmpty() ? "无" : releaseBranches.get(0);
        String latestHotfixVersion = hotfixBranches.isEmpty() ? "无" : hotfixBranches.get(0);

        String branchName = ui().promptText(
                shell(event),
                "ZeroGit: Start New Hotfix",
                "请输入 Hotfix 分支（SemVer）\n"
                        + "1. 最新的 tag：" + latestTag.getTagName() + "\n"
                        + "2. 最新的 release：" + latestReleaseVersion + "\n"
                        + "3. 最新的 hotfix：" + latestHotfixVersion + "\n"
                        + "建议 hotfix 版本：" + suggestedBranch + "。请输入 hotfix 版本。",
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
                "即将基于生产 Tag " + latestTag.getTagName() + " 创建新的 hotfix：\n" + branchName + "\n\n请确认新生成的 hotfix 是否正确？")) {
            return null;
        }

        runScriptJob(
                shell(event),
                "Start New Hotfix",
                project,
                buildRequest(
                        repoRoot,
                        "StartNewHotfix.sh",
                        flowService().buildStartHotfixArgs(group, branchName, latestTag.getTagName())),
                true);
        return null;
    }

    private HotfixBaseTagInfo latestHotfixBaseTag(String repoRoot) throws ExecutionException {
        try {
            return gitRepositoryService().getLatestRemoteHotfixBaseTag(repoRoot);
        } catch (Exception e) {
            throw new ExecutionException("Failed to inspect remote tags.", e);
        }
    }

    private List<String> listReleaseBranches(String repoRoot, String group) throws ExecutionException {
        try {
            return gitRepositoryService().listReleaseBranches(repoRoot, group, true);
        } catch (Exception e) {
            throw new ExecutionException("Failed to list release branches.", e);
        }
    }

    private List<String> listHotfixBranches(String repoRoot, String group) throws ExecutionException {
        try {
            return gitRepositoryService().listHotfixBranches(repoRoot, group, true);
        } catch (Exception e) {
            throw new ExecutionException("Failed to list hotfix branches.", e);
        }
    }
}
