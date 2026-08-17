package com.zerofinance.zerogit.eclipse.actions;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;

import com.zerofinance.zerogit.eclipse.git.VersionService;
import com.zerofinance.zerogit.eclipse.git.VersionService.HotfixBaseTagInfo;

public class StartNewReleaseHandler extends AbstractZeroGitHandler {
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IProject project = requireProject(event);
        String repoRoot = requireRepositoryRoot(event);
        String group = requireGroupSelection(shell(event));

        if (!ui().confirm(
                shell(event),
                "ZeroGit: Start New Release",
                "确认好准备提测了吗？是否已执行FinishFeature删除本地多余的feature分支？\n\n"
                        + "1. StartNewRelease只能在提测时执行一次，maven项目会自动更新pom.xml版本，并打上-RC1后缀。\n"
                        + "2. 后续无需再次打release分支，直接在release分支上进行bug的修复。如需升级maven版本，执行MavenChange操作即可。")) {
            return null;
        }
        if (!confirmPomSnapshotIfPresent(shell(event), repoRoot)) {
            return null;
        }

        VersionService versionService = new VersionService();
        List<String> releaseBranches = listReleaseBranches(repoRoot, group);
        List<String> hotfixBranches = listHotfixBranches(repoRoot, group);
        List<String> allBranches = new ArrayList<String>();
        allBranches.addAll(listAllReleaseBranches(repoRoot));
        allBranches.addAll(listAllHotfixBranches(repoRoot));
        List<String> remoteTags = listRemoteReleaseOrHotfixTags(repoRoot);
        allBranches.addAll(remoteTags);
        HotfixBaseTagInfo latestTag = versionService.findLatestHotfixBaseTag(remoteTags);

        String suggestedBranch = versionService.suggestNextRelease(allBranches, merge(releaseBranches, hotfixBranches), group);
        String latestTagText = latestTag == null ? "无" : latestTag.getTagName();
        String latestReleaseVersion = releaseBranches.isEmpty() ? "无" : releaseBranches.get(0);
        String latestHotfixVersion = hotfixBranches.isEmpty() ? "无" : hotfixBranches.get(0);
        String branchName = ui().promptText(
                shell(event),
                "ZeroGit: Start New Release",
                "请输入 Release 分支（SemVer）\n"
                        + "1. 最新的 tag：" + latestTagText + "\n"
                        + "2. 最新的 release：" + latestReleaseVersion + "\n"
                        + "3. 最新的 hotfix：" + latestHotfixVersion + "\n"
                        + "建议 release 版本：" + suggestedBranch + "。请输入 release 版本。",
                suggestedBranch);
        if (branchName == null) {
            return null;
        }

        String validationMessage = flowService().validateReleaseBranchName(group, branchName);
        if (validationMessage != null) {
            ui().showError(shell(event), "ZeroGit: Start New Release", validationMessage);
            return null;
        }

        runScriptJob(
                shell(event),
                "Start New Release",
                project,
                buildRequest(repoRoot, "StartNewRelease.sh", flowService().buildStartReleaseArgs(group, branchName)),
                true);
        return null;
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

    private List<String> listAllReleaseBranches(String repoRoot) throws ExecutionException {
        try {
            return gitRepositoryService().listAllReleaseBranches(repoRoot, true);
        } catch (Exception e) {
            throw new ExecutionException("Failed to list all release branches.", e);
        }
    }

    private List<String> listAllHotfixBranches(String repoRoot) throws ExecutionException {
        try {
            return gitRepositoryService().listAllHotfixBranches(repoRoot, true);
        } catch (Exception e) {
            throw new ExecutionException("Failed to list all hotfix branches.", e);
        }
    }

    private List<String> listRemoteReleaseOrHotfixTags(String repoRoot) throws ExecutionException {
        try {
            return gitRepositoryService().listRemoteReleaseOrHotfixTags(repoRoot);
        } catch (Exception e) {
            throw new ExecutionException("Failed to inspect remote tags.", e);
        }
    }

    private List<String> merge(List<String> left, List<String> right) {
        List<String> merged = new ArrayList<String>();
        merged.addAll(left);
        merged.addAll(right);
        return merged;
    }
}
