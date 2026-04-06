package com.zerofinance.zerogitdeploy.handler;

import com.google.common.collect.Lists;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.terminal.JBTerminalWidget;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.zerofinance.zerogitdeploy.exception.DeployPluginException;
import com.zerofinance.zerogitdeploy.setting.ZeroGitDeploySetting;
import com.zerofinance.zerogitdeploy.tools.CommandUtils;
import com.zerofinance.zerogitdeploy.tools.DeployCmdExecuter;
import com.zerofinance.zerogitdeploy.tools.ExecuteResult;
import com.zerofinance.zerogitdeploy.tools.MessagesUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.ShellTerminalWidget;
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory;
import org.jetbrains.plugins.terminal.TerminalView;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Continuation after gitCheck and script preparation finished in background. */
@FunctionalInterface
interface GitCheckContinuation {
    void run(String rootPath, String script) throws Exception;
}

@SuppressWarnings("restriction")
public class ZeroGitFlowHandler {
    private static final Pattern FEATURE_SUFFIX_PATTERN = Pattern.compile("^\\d+-\\S.*$");
    private static final Pattern SEMVER_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)$");
    private static final Pattern MAVEN_VERSION_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(-SNAPSHOT|-RC\\d+)?$", Pattern.CASE_INSENSITIVE);
    private static final String[] GROUPS = new String[]{"a", "b"};
    private static final String TITLE = "ZeroGit";
    private static final Logger LOG = Logger.getInstance(ZeroGitFlowHandler.class);

    private static final class HotfixBaseTagInfo {
        private final String tagName;
        private final String version;
        private final String timestamp;

        private HotfixBaseTagInfo(String tagName, String version, String timestamp) {
            this.tagName = tagName;
            this.version = version;
            this.timestamp = timestamp;
        }
    }

    /** Runs script preparation in background (no gitCheck), then invokes continuation on EDT. Used by MavenChange. */
    private void runWithScriptInBackground(String rootPath, String scriptFileName, GitCheckContinuation continuation) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "ZeroGit: 准备脚本...") {
            private String resultRootPath;
            private String resultScript;
            private Exception runError;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    CommandUtils.clearZeroGitScriptCache();
                    resultRootPath = rootPath;
                    resultScript = CommandUtils.processZeroGitScript(rootPath, scriptFileName);
                } catch (Exception e) {
                    runError = e;
                }
            }

            @Override
            public void onFinished() {
                if (runError != null) {
                    String detail = MessagesUtils.buildDetailedErrorMessage(runError);
                    String summary = runError.getMessage();
                    if (summary == null || summary.trim().isEmpty()) {
                        summary = "脚本准备失败，请点击“复制完整错误”获取详细信息。";
                    }
                    MessagesUtils.showErrorWithDetails(project, "ZeroGit", summary, detail);
                    return;
                }
                try {
                    continuation.run(resultRootPath, resultScript);
                } catch (Exception e) {
                    String detail = MessagesUtils.buildDetailedErrorMessage(e);
                    String summary = e.getMessage();
                    if (summary == null || summary.trim().isEmpty()) {
                        summary = "执行失败，请点击“复制完整错误”获取详细信息。";
                    }
                    MessagesUtils.showErrorWithDetails(project, "ZeroGit", summary, detail);
                }
            }
        });
    }

    /** Runs gitCheck and script preparation in background, then invokes continuation on EDT to avoid freezing the IDE. */
    private void runWithGitCheckInBackground(String rootPath, String scriptFileName, GitCheckContinuation continuation) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "ZeroGit: gitCheck...") {
            private String resultRootPath;
            private String resultScript;
            private Exception runError;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    runGitCheck(rootPath);
                    CommandUtils.clearZeroGitScriptCache();
                    resultRootPath = rootPath;
                    resultScript = CommandUtils.processZeroGitScript(rootPath, scriptFileName);
                } catch (Exception e) {
                    runError = e;
                }
            }

            @Override
            public void onFinished() {
                if (runError != null) {
                    String detail = MessagesUtils.buildDetailedErrorMessage(runError);
                    String summary = runError.getMessage();
                    if (summary == null || summary.trim().isEmpty()) {
                        summary = "gitCheck 或脚本准备失败，请点击“复制完整错误”获取详细信息。";
                    }
                    MessagesUtils.showErrorWithDetails(project, "ZeroGit", summary, detail);
                    return;
                }
                try {
                    continuation.run(resultRootPath, resultScript);
                } catch (Exception e) {
                    String detail = MessagesUtils.buildDetailedErrorMessage(e);
                    String summary = e.getMessage();
                    if (summary == null || summary.trim().isEmpty()) {
                        summary = "执行失败，请点击“复制完整错误”获取详细信息。";
                    }
                    MessagesUtils.showErrorWithDetails(project, "ZeroGit", summary, detail);
                }
            }
        });
    }

    @FunctionalInterface
    private interface GitCheckContinuation {
        void run(String rootPath, String script) throws Exception;
    }

    private final Project project;
    private final String modulePath;
    private final String moduleName;

    public ZeroGitFlowHandler(AnActionEvent event) {
        this.project = event.getProject();
        if (project == null) {
            throw new DeployPluginException("Project is required.");
        }
        VirtualFile vFile = event.getData(PlatformDataKeys.VIRTUAL_FILE);
        if (vFile != null) {
            this.modulePath = vFile.getPath();
        } else if (StringUtils.isNotBlank(project.getBasePath())) {
            this.modulePath = project.getBasePath();
        } else {
            throw new DeployPluginException("Cannot resolve current project path.");
        }
        this.moduleName = new File(modulePath).getName();
        requireGitHomeOnWindows();
    }

    public void startNewFeature() throws Exception {
        debugLog("command triggered", "Start New Feature");
        String groupName = requireGroupName();
        String rootPath = getRootPath();
        runWithGitCheckInBackground(rootPath, "StartNewFeature.sh", (rPath, script) -> {
            String prefix = "feature/" + groupName + "/";
            String input = Messages.showInputDialog(
                    "请输入 Feature 分支名（需以 " + prefix + " 开头）",
                    "ZeroGit: Start New Feature",
                    Messages.getInformationIcon(),
                    prefix,
                    nonEmptyValidator()
            );
            if (StringUtils.isBlank(input)) {
                throw new DeployPluginException("已取消 Start New Feature。");
            }
            if (!input.startsWith(prefix)) {
                throw new DeployPluginException("Feature 分支必须以 " + prefix + " 开头。");
            }
            String suffix = input.substring(prefix.length());
            if (!FEATURE_SUFFIX_PATTERN.matcher(suffix).matches()) {
                throw new DeployPluginException("Feature 后缀格式必须为 数字-描述，例如 001-login。");
            }
            confirmAndRunInTerminal("Start New Feature", rPath, script, Lists.newArrayList(groupName, input));
        });
    }

    public void finishFeature() throws Exception {
        debugLog("command triggered", "Finish Feature");
        String groupName = requireGroupName();
        if (!yes("请确认：是否已在 GitLab 中 MR 到 develop-" + groupName + " 并完成 Merge？\n继续只会删除本地 feature 分支。", "ZeroGit: Finish Feature")) {
            return;
        }
        String rootPath = getRootPath();
        runWithGitCheckInBackground(rootPath, "FinishFeature.sh", (rPath, script) -> {
            List<String> branches = listLocalFeatureBranches(rPath, groupName);
            if (branches.isEmpty()) {
                throw new DeployPluginException("No local feature branch found for group \"" + groupName + "\"");
            }
            String selected = chooseBranch("请选择要结束的 feature 分支", "ZeroGit: Finish Feature", branches);
            if (StringUtils.isBlank(selected)) {
                return;
            }
            confirmAndRunInTerminal("Finish Feature", rPath, script, Lists.newArrayList(groupName, selected));
        });
    }

    public void rebaseFeature() throws Exception {
        debugLog("command triggered", "Rebase Feature");
        String groupName = requireGroupName();
        String rootPath = getRootPath();
        runWithScriptInBackground(rootPath, "RebaseFeature.sh", (rPath, script) -> {
            String currentBranch = getCurrentBranch(rPath);
            String requiredPrefix = "feature/" + groupName + "/";
            if (!currentBranch.startsWith(requiredPrefix)) {
                throw new DeployPluginException("当前分支不是 " + requiredPrefix + " 开头，无法 Rebase Feature。");
            }
            confirmAndRunInTerminal("Rebase Feature", rPath, script, Lists.newArrayList(groupName, currentBranch));
        });
    }

    public void mavenChange() throws Exception {
        debugLog("command triggered", "Maven Change");
        String groupName = requireGroupName();
        String rootPath = getMavenProjectRootPath();
        runWithScriptInBackground(rootPath, "MavenChange.sh", (rPath, script) -> {
            String changeType = chooseMavenChangeType();
            if (StringUtils.isBlank(changeType)) {
                return;
            }
            String currentPomVersion = readMavenPomVersion(rPath);
            if ("release".equals(changeType)) {
                boolean hasRc = Pattern.compile("-RC\\d+$", Pattern.CASE_INSENSITIVE).matcher(StringUtils.defaultString(currentPomVersion)).find();
                boolean hasSnapshot = StringUtils.endsWithIgnoreCase(currentPomVersion, "-SNAPSHOT");
                if (!hasRc && !hasSnapshot) {
                    Messages.showErrorDialog(project, "你只能基于RC或SNAPSHOT进行操作", "ZeroGit: Maven Change");
                    return;
                }
            }
            String suggestedVersion = buildSuggestedMavenVersion(currentPomVersion, changeType);
            if ("release".equals(changeType) && suggestedVersion == null) {
                Messages.showErrorDialog(project, "你只能基于RC或SNAPSHOT进行操作", "ZeroGit: Maven Change");
                return;
            }
            String inputVersion = Messages.showInputDialog(
                    "请输入 Maven 版本号",
                    "ZeroGit: Maven Change",
                    Messages.getInformationIcon(),
                    suggestedVersion != null ? suggestedVersion : "",
                    nonEmptyValidator()
            );
            if (StringUtils.isBlank(inputVersion)) {
                return;
            }
            String mavenVersion = inputVersion.trim();
            if (!MAVEN_VERSION_PATTERN.matcher(mavenVersion).matches()) {
                throw new DeployPluginException("Maven version must be x.y.z, x.y.z-SNAPSHOT or x.y.z-RCN (N为数字).");
            }
            if ("release".equals(changeType) && StringUtils.endsWithIgnoreCase(mavenVersion, "-SNAPSHOT")) {
                throw new DeployPluginException("Release 版本不能以 -SNAPSHOT 结尾。");
            }
            if ("snapshot".equals(changeType) && !StringUtils.endsWithIgnoreCase(mavenVersion, "-SNAPSHOT")) {
                throw new DeployPluginException("Snapshot 版本必须以 -SNAPSHOT 结尾。");
            }
            confirmAndRunInTerminal("Maven Change", rPath, script, Lists.newArrayList(groupName, mavenVersion));
        });
    }

    public void generateCommitMessage() throws Exception {
        debugLog("command triggered", "Generate Commit Message");
        String rootPath = getRootPath();
        CommandUtils.clearZeroGitScriptCache();
        if (!gitRepoHasStagedChanges(rootPath)) {
            Messages.showWarningDialog(project, "请先执行 git add 后再生成 Commit Message", "ZeroGit: Generate Commit Message");
            return;
        }
        String script = CommandUtils.processZeroGitScript(rootPath, "GenCommitMessage.sh");
        confirmAndRunInTerminal("Generate Commit Message", rootPath, script, Lists.newArrayList());
    }

    public void aiCodeReview() throws Exception {
        debugLog("command triggered", "AI Code Review");
        String rootPath = getRootPath();
        CommandUtils.clearZeroGitScriptCache();
        if (!gitRepoHasStagedChanges(rootPath)) {
            Messages.showWarningDialog(project, "请先执行 git add 后再运行 AI Code Review", "ZeroGit: AI Code Review");
            return;
        }
        String script = CommandUtils.processZeroGitScript(rootPath, "AiCodeReview.sh");
        confirmAndRunInTerminal("AI Code Review", rootPath, script, Lists.newArrayList());
    }

    public void startNewRelease() throws Exception {
        debugLog("command triggered", "Start New Release");
        String groupName = requireGroupName();
        if (!yes("确认好准备提测了吗？是否已执行FinishFeature删除本地多余的feature分支？\n\n1. StartNewRelease只能在提测时执行一次，maven项目会自动更新pom.xml版本，并打上-RC1后缀。\n2. 后续无需再次打release分支，直接在release分支上进行bug的修复。如需升级maven版本，执行MavenChange操作即可。", "ZeroGit: Start New Release")) {
            return;
        }
        String rootPath = getRootPath();
        runWithGitCheckInBackground(rootPath, "StartNewRelease.sh", (rPath, script) -> {
            if (!confirmPomSnapshotIfPresent(rPath)) {
                return;
            }
            gitFetchOriginPrune(rPath);
            HotfixBaseTagInfo latestTag = getLatestRemoteHotfixBaseTag(rPath, groupName);
            // 为了满足“按最后三位数字递增”的需求：maxVersion 取全局（跨所有 group），
            // 但 findNextAvailableVersion 只需要避开当前 group 内已存在的 release/hotfix 版本。
            List<String> releases = listReleaseBranches(rPath, groupName, true, true);
            List<String> hotfixes = listHotfixBranches(rPath, groupName, true, true);
            List<String> remoteReleasesInGroup = listReleaseBranches(rPath, groupName, false, true);
            List<String> remoteHotfixesInGroup = listHotfixBranches(rPath, groupName, false, true);
            List<String> remoteReleasesAll = listAllReleaseBranches(rPath, false, true);
            List<String> remoteHotfixesAll = listAllHotfixBranches(rPath, false, true);
            String maxVersion = getMaxSemverVersion(latestTag == null ? null : latestTag.version, remoteReleasesAll, remoteHotfixesAll);
            String suggested = "1.0.0";
            if (!remoteReleasesAll.isEmpty() || !remoteHotfixesAll.isEmpty() || latestTag != null) {
                // 新版本号规则：累计中间段（minor），而不是尾数（patch）
                // 例如：1.0.1 -> 1.1.0
                suggested = findNextAvailableVersion(nextMinor(maxVersion), remoteReleasesInGroup, remoteHotfixesInGroup);
            }
            String prefix = "release/" + groupName + "/";
            String latestTagText = latestTag == null ? "无" : latestTag.tagName;
            String latestReleaseVersion = remoteReleasesAll.isEmpty() ? "无" : extractVersion(remoteReleasesAll.get(0));
            String latestHotfixVersion = remoteHotfixesAll.isEmpty() ? "无" : extractVersion(remoteHotfixesAll.get(0));
            String value = Messages.showInputDialog(
                    "请输入 Release 分支（SemVer）\n"
                            + "1. 最新的 tag：" + latestTagText + "\n"
                            + "2. 最新的 release：" + latestReleaseVersion + "\n"
                            + "3. 最新的 hotfix：" + latestHotfixVersion + "\n"
                            + "建议 release 版本：" + suggested + "。请输入 release 版本。",
                    "ZeroGit: Start New Release",
                    Messages.getInformationIcon(),
                    prefix + suggested,
                    nonEmptyValidator()
            );
            if (StringUtils.isBlank(value)) {
                return;
            }
            if (!value.startsWith(prefix)) {
                throw new DeployPluginException("Release 分支必须以 " + prefix + " 开头。");
            }
            String version = value.substring(prefix.length());
            ensureSemver(version, "Release 版本格式无效，必须是 X.Y.Z");
            ensureVersionNotExists(version, releases, "release");
            ensureVersionNotExists(version, hotfixes, "hotfix");

            confirmAndRunInTerminal("Start New Release", rPath, script, Lists.newArrayList(groupName, value));
        });
    }

    public void finishRelease() throws Exception {
        debugLog("command triggered", "Finish Release");
        if (!acknowledgeFinishUsageNotice("ZeroGit: Finish Release")) {
            return;
        }
        if (!yes("运维是否已完成上线？", "ZeroGit: Finish Release")) {
            return;
        }
        String rootPath = getRootPath();
        runWithGitCheckInBackground(rootPath, "FinishRelease.sh", (rPath, script) -> {
            List<String> releases = listAllReleaseBranches(rPath, true);
            if (releases.isEmpty()) {
                throw new DeployPluginException("No release branch found.");
            }
            String selected = chooseBranch("请选择要结束的 release 分支", "ZeroGit: Finish Release", releases);
            if (StringUtils.isBlank(selected)) {
                return;
            }
            List<String> params = Lists.newArrayList(selected);
            confirmAndRunSyncAsync("Finish Release", rPath, script, params, "release");
        });
    }

    public void startNewHotfix() throws Exception {
        debugLog("command triggered", "Start New Hotfix");
        String groupName = requireGroupName();
        if (!yes("请确认上线后是否有及时合并代码到 main/develop/release/hotfix 分支？hotfix会基于最新的生产环境tag来创建。", "ZeroGit: Start New Hotfix")) {
            return;
        }
        String rootPath = getRootPath();
        runWithGitCheckInBackground(rootPath, "StartNewHotfix.sh", (rPath, script) -> {
            if (!confirmPomSnapshotIfPresent(rPath)) {
                return;
            }
            gitFetchOriginPrune(rPath);
            HotfixBaseTagInfo latestTag = getLatestRemoteHotfixBaseTag(rPath, groupName);
            if (latestTag == null) {
                throw new DeployPluginException("未找到符合 release/" + groupName + "/X.Y.Z-YYYYMMDDHHmm 或 hotfix/" + groupName + "/X.Y.Z-YYYYMMDDHHmm 规则的远程 tag，Start New Hotfix 已中断。");
            }
            List<String> releases = listReleaseBranches(rPath, groupName, true, true);
            List<String> hotfixes = listHotfixBranches(rPath, groupName, true, true);
            List<String> remoteReleasesInGroup = listReleaseBranches(rPath, groupName, false, true);
            List<String> remoteHotfixesInGroup = listHotfixBranches(rPath, groupName, false, true);
            List<String> remoteReleasesAll = listAllReleaseBranches(rPath, false, true);
            List<String> remoteHotfixesAll = listAllHotfixBranches(rPath, false, true);
            String maxVersion = getMaxSemverVersion(latestTag.version, remoteReleasesAll, remoteHotfixesAll);
            // hotfix：递增尾号（patch），与 release 的 minor 递增区分
            String suggested = findNextAvailableVersion(nextPatch(maxVersion), remoteReleasesInGroup, remoteHotfixesInGroup, this::nextPatch);
            String prefix = "hotfix/" + groupName + "/";
            String latestReleaseVersion = remoteReleasesAll.isEmpty() ? "无" : extractVersion(remoteReleasesAll.get(0));
            String latestHotfixVersion = remoteHotfixesAll.isEmpty() ? "无" : extractVersion(remoteHotfixesAll.get(0));
            String value = Messages.showInputDialog(
                    "请输入 Hotfix 分支（SemVer）\n"
                            + "1. 最新的 tag：" + latestTag.tagName + "\n"
                            + "2. 最新的 release：" + latestReleaseVersion + "\n"
                            + "3. 最新的 hotfix：" + latestHotfixVersion + "\n"
                            + "建议 hotfix 版本：" + suggested + "。请输入 hotfix 版本。",
                    "ZeroGit: Start New Hotfix",
                    Messages.getInformationIcon(),
                    prefix + suggested,
                    nonEmptyValidator()
            );
            if (StringUtils.isBlank(value)) {
                return;
            }
            if (!value.startsWith(prefix)) {
                throw new DeployPluginException("Hotfix 分支必须以 " + prefix + " 开头。");
            }
            String version = value.substring(prefix.length());
            ensureSemver(version, "Hotfix 版本格式无效，必须是 X.Y.Z");
            ensureVersionNotExists(version, hotfixes, "hotfix");
            ensureVersionNotExists(version, releases, "release");
            if (!yes("即将基于生产 Tag " + latestTag.tagName + " 创建新的 hotfix：\n" + value + "\n\n请确认新生成的 hotfix 是否正确？", "ZeroGit: Start New Hotfix")) {
                return;
            }

            confirmAndRunInTerminal("Start New Hotfix", rPath, script, Lists.newArrayList(groupName, value, latestTag.tagName));
        });
    }

    public void finishHotfix() throws Exception {
        debugLog("command triggered", "Finish Hotfix");
        if (!acknowledgeFinishUsageNotice("ZeroGit: Finish Hotfix")) {
            return;
        }
        if (!yes("运维是否已完成上线？", "ZeroGit: Finish Hotfix")) {
            return;
        }
        String rootPath = getRootPath();
        runWithGitCheckInBackground(rootPath, "FinishRelease.sh", (rPath, script) -> {
            List<String> hotfixes = listAllHotfixBranches(rPath, true);
            if (hotfixes.isEmpty()) {
                throw new DeployPluginException("No hotfix branch found.");
            }
            String selected = chooseBranch("请选择要结束的 hotfix 分支", "ZeroGit: Finish Hotfix", hotfixes);
            if (StringUtils.isBlank(selected)) {
                return;
            }
            List<String> params = Lists.newArrayList(selected);
            confirmAndRunSyncAsync("Finish Hotfix", rPath, script, params, "hotfix");
        });
    }

    private String requireGroupName() {
        // 不直接依赖全局配置值，而是每次在需要 group 的场景下弹出下拉选择。
        String configured = ZeroGitDeploySetting.getGroupName();
        String placeholderLabel = "Please select a group before running any task";
        String groupALabel = "Group A";
        String groupBLabel = "Group B";

        // keep dropdown data source consistent with settings validation
        List<String> labels = Arrays.asList(placeholderLabel, groupALabel, groupBLabel);
        String defaultLabel;
        if ("a".equals(configured)) {
            defaultLabel = groupALabel;
        } else if ("b".equals(configured)) {
            defaultLabel = groupBLabel;
        } else {
            defaultLabel = placeholderLabel;
        }

        String selected = Messages.showEditableChooseDialog(
                placeholderLabel,
                "ZeroGit",
                null,
                labels.toArray(new String[0]),
                defaultLabel,
                null
        );

        if (StringUtils.isBlank(selected) || placeholderLabel.equals(selected)) {
            throw new DeployPluginException("Please select group \"a\" or \"b\" before running tasks.");
        }
        if (groupALabel.equals(selected)) return "a";
        if (groupBLabel.equals(selected)) return "b";
        // 允许用户手动输入 a/b（因为是 editable dialog）
        if ("a".equals(selected) || "b".equals(selected)) return selected;

        throw new DeployPluginException("Invalid group selection: " + selected);
    }

    private void requireGitHomeOnWindows() {
        if (!SystemUtils.IS_OS_WINDOWS) {
            return;
        }
        if (StringUtils.isNotBlank(ZeroGitDeploySetting.getGitHome())) {
            return;
        }
        Messages.showWarningDialog("Windows 环境请先配置 Git Home。", "ZeroGit");
        ShowSettingsUtil.getInstance().showSettingsDialog(project, ZeroGitDeploySetting.class);
        throw new DeployPluginException("请先在 Git Deploy Settings 中配置 Git Home。");
    }

    private String getRootPath() {
        return CommandUtils.getRootProjectPath(modulePath);
    }

    private String getMavenProjectRootPath() {
        String gitRootPath = getRootPath();
        if (StringUtils.isBlank(gitRootPath)) {
            throw new DeployPluginException("无法获取 Git 根目录。");
        }
        File gitRoot = new File(gitRootPath);
        if (!gitRoot.isDirectory()) {
            throw new DeployPluginException("Git 根目录无效。");
        }
        File currentDir = toDirectory(modulePath);
        if (currentDir == null) {
            throw new DeployPluginException("无法确定当前文件所在目录。");
        }
        String currentCanonicalPath;
        try {
            currentCanonicalPath = currentDir.getCanonicalPath();
        } catch (IOException e) {
            currentCanonicalPath = currentDir.getAbsolutePath();
        }
        List<File> mavenRoots = new ArrayList<>();
        collectMavenRootsUnder(gitRoot, mavenRoots);
        File bestRoot = null;
        int bestPathLength = Integer.MAX_VALUE;
        String separator = File.separator;
        for (File root : mavenRoots) {
            String rootPath;
            try {
                rootPath = root.getCanonicalPath();
            } catch (IOException e) {
                rootPath = root.getAbsolutePath();
            }
            boolean underThisRoot = currentCanonicalPath.equals(rootPath)
                    || currentCanonicalPath.startsWith(rootPath + separator);
            if (underThisRoot && rootPath.length() < bestPathLength) {
                bestRoot = root;
                bestPathLength = rootPath.length();
            }
        }
        if (bestRoot == null) {
            throw new DeployPluginException("在当前选择目录及其上级目录中未找到有效的 Maven 项目（缺少可用 pom.xml）。请先选择子项目目录后重试。");
        }
        return bestRoot.getPath();
    }

    /** 从 gitRoot 往下递归收集所有含有效 pom.xml 的 Maven 项目根目录。 */
    private void collectMavenRootsUnder(File dir, List<File> result) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        if (hasValidMavenPom(dir)) {
            result.add(dir);
        }
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory() && !".git".equals(child.getName())) {
                    collectMavenRootsUnder(child, result);
                }
            }
        }
    }

    private File toDirectory(String path) {
        if (StringUtils.isBlank(path)) {
            return null;
        }
        File file = new File(path);
        if (!file.exists()) {
            return null;
        }
        if (file.isDirectory()) {
            return file;
        }
        return file.getParentFile();
    }

    private boolean sameFile(File left, File right) {
        try {
            return left.getCanonicalFile().equals(right.getCanonicalFile());
        } catch (IOException e) {
            return left.equals(right);
        }
    }

    private boolean hasValidMavenPom(File directory) {
        if (directory == null) {
            return false;
        }
        File pomFile = new File(directory, "pom.xml");
        if (!pomFile.exists() || !pomFile.isFile()) {
            return false;
        }
        return looksLikeMavenPom(pomFile);
    }

    private boolean looksLikeMavenPom(File pomFile) {
        try {
            String content = Files.readString(pomFile.toPath(), StandardCharsets.UTF_8);
            return content.contains("<project");
        } catch (IOException e) {
            debugLog("failed to read pom.xml", e.getMessage());
            return false;
        }
    }

    private String chooseMavenChangeType() {
        String[] types = new String[]{"release", "snapshot"};
        String selected = Messages.showEditableChooseDialog(
                "请选择 Maven 版本类型",
                "ZeroGit: Maven Change",
                Messages.getInformationIcon(),
                types,
                types[0],
                null
        );
        if (StringUtils.isBlank(selected)) {
            return null;
        }
        String normalized = selected.trim().toLowerCase(Locale.ROOT);
        if (!"release".equals(normalized) && !"snapshot".equals(normalized)) {
            throw new DeployPluginException("仅支持选择 release 或 snapshot。");
        }
        return normalized;
    }

    private String readMavenPomVersion(String rootPath) {
        File pomFile = new File(rootPath, "pom.xml");
        if (!pomFile.exists() || !pomFile.isFile()) {
            return null;
        }
        try {
            String content = Files.readString(pomFile.toPath(), StandardCharsets.UTF_8);
            String noComments = content.replaceAll("(?s)<!--.*?-->", "");
            String noParentBlock = noComments.replaceAll("(?s)<parent>.*?</parent>", "");
            Matcher matcher = Pattern.compile("<version>\\s*([^<\\s]+)\\s*</version>").matcher(noParentBlock);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
            return null;
        } catch (IOException e) {
            debugLog("failed to parse pom version", e.getMessage());
            return null;
        }
    }

    private String buildSuggestedMavenVersion(String currentVersion, String changeType) {
        String raw = StringUtils.defaultString(currentVersion).trim();
        if (raw.isEmpty()) {
            return "release".equals(changeType) ? null : "1.0.1-SNAPSHOT";
        }
        if ("snapshot".equals(changeType)) {
            // 如果有 - 字符，去掉 - 后面的内容，得到 base x.y.z，递增尾数后添加 -SNAPSHOT
            String baseVersion = raw.contains("-") ? raw.split("-")[0].trim() : raw.replaceFirst("(?i)-SNAPSHOT$", "");
            String nextVersion = "1.0.1";
            Matcher matcher = SEMVER_PATTERN.matcher(baseVersion);
            if (matcher.matches()) {
                int major = Integer.parseInt(matcher.group(1));
                int minor = Integer.parseInt(matcher.group(2));
                int patch = Integer.parseInt(matcher.group(3)) + 1;
                nextVersion = major + "." + minor + "." + patch;
            }
            return nextVersion + "-SNAPSHOT";
        }
        // release: -RCN 递增 N；-SNAPSHOT 去掉；否则返回 null
        Matcher rcMatcher = Pattern.compile("-RC(\\d+)$", Pattern.CASE_INSENSITIVE).matcher(raw);
        if (rcMatcher.find()) {
            int n = Integer.parseInt(rcMatcher.group(1));
            String base = raw.substring(0, rcMatcher.start());
            return base + "-RC" + (n + 1);
        }
        if (StringUtils.endsWithIgnoreCase(raw, "-SNAPSHOT")) {
            String base = raw.replaceFirst("(?i)-SNAPSHOT$", "");
            return base + "-RC1";
        }
        return null;
    }

    private void runGitCheck(String rootPath) throws Exception {
        CommandUtils.clearZeroGitScriptCache();
        debugLog("cache cleaned", rootPath);
        if (ZeroGitDeploySetting.isCheckGitVersion()) {
            ensureGitVersion(rootPath);
        }
        String script = CommandUtils.processZeroGitScript(rootPath, "gitCheck.sh");
        debugLog("execute gitCheck script", script);
        ExecuteResult result = DeployCmdExecuter.exec(rootPath, script, new ArrayList<>(), true);
        if (result.getCode() != 0) {
            throw new DeployPluginException("gitCheck 执行失败，exitCode=" + result.getCode() + "，详情：" +
                    StringUtils.defaultString(result.getResult(), "(no output)"));
        }
        debugLog("gitCheck finished", "ok");
    }

    private void ensureGitVersion(String rootPath) throws Exception {
        ExecuteResult result = execGitArgs(rootPath, "version");
        String text = result.getResult();
        debugLog("detected git version output", text);
        Matcher matcher = Pattern.compile("(\\d+)\\.(\\d+)").matcher(text);
        if (!matcher.find()) {
            throw new DeployPluginException("无法识别 Git 版本，请确认 Git 已安装。");
        }
        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        if (major < 2 || (major == 2 && minor < 29)) {
            throw new DeployPluginException("Git 版本需要 >= 2.29，请先升级。");
        }
    }

    private void confirmAndRunInTerminal(String commandName, String rootPath, String script, List<String> params) throws IOException {
        String message = buildConfirmMessage(commandName, rootPath, script, params);
        if (!yes(message, "ZeroGit Confirm")) {
            debugLog("script execution cancelled by user", commandName);
            return;
        }
        debugLog("send command to terminal", toBashCommand(rootPath, script, params));
        runInTerminal(rootPath, script, params);
        MessagesUtils.showMessage(project,
                commandName + " executed done, please check the logs in terminal.",
                moduleName + ": Done",
                NotificationType.INFORMATION);
    }

    private void confirmAndRunSyncAsync(String commandName, String rootPath, String script, List<String> params, String branchType) {
        String message = buildConfirmMessage(commandName, rootPath, script, params);
        if (!yes(message, "ZeroGit Confirm")) {
            debugLog("script execution cancelled by user", commandName);
            return;
        }
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(DeployCmdExecuter.PLUGIN_ID);
        if (toolWindow == null) {
            throw new DeployPluginException("ToolWindow not found: " + DeployCmdExecuter.PLUGIN_ID);
        }
        toolWindow.setAvailable(true, null);
        toolWindow.show(null);

        ConsoleView console = getOrCreateConsole(toolWindow);
        Task.Backgroundable task = new Task.Backgroundable(project, "ZeroGit " + commandName + "...") {
            private ExecuteResult result;
            private Exception runError;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    debugLog("execute sync command", commandName + " -> " + script + " " + String.join(" ", params));
                    result = DeployCmdExecuter.exec(console, rootPath, script, params, true);
                } catch (Exception e) {
                    runError = e;
                }
            }

            @Override
            public void onFinished() {
                if (runError != null) {
                    String details = MessagesUtils.buildDetailedErrorMessage(runError);
                    MessagesUtils.showErrorWithDetails(project,
                            commandName + " failed",
                            commandName + " 执行异常，请点击“复制完整错误”获取详细信息。",
                            details);
                    return;
                }
                if (result == null) {
                    MessagesUtils.showMessage(project,
                            commandName + " 未返回执行结果，请检查日志。",
                            moduleName + ": Error",
                            NotificationType.ERROR);
                    return;
                }
                if (result.getCode() != 0) {
                    debugLog("sync command failed", result.getResult());
                    String output = StringUtils.defaultString(result.getResult(), "(no output)");
                    MessagesUtils.showErrorWithDetails(project,
                            commandName + " failed",
                            commandName + " 失败，exitCode=" + result.getCode(),
                            output);
                    return;
                }
                MessagesUtils.showMessage(project,
                        commandName + " executed done, please check logs.",
                        moduleName + ": Done",
                        NotificationType.INFORMATION);
                parseRemainingAndNotify(result.getResult(), branchType);
            }
        };
        ProgressManager.getInstance().run(task);
    }

    private ConsoleView getOrCreateConsole(ToolWindow toolWindow) {
        Content content = toolWindow.getContentManager().findContent(TITLE);
        ConsoleView console;
        if (content == null) {
            console = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
            Content newContent = toolWindow.getContentManager().getFactory().createContent(console.getComponent(), TITLE, false);
            toolWindow.getContentManager().addContent(newContent);
            toolWindow.getContentManager().setSelectedContent(newContent);
            return console;
        }
        if (content.getComponent() instanceof ConsoleViewImpl) {
            console = (ConsoleViewImpl) content.getComponent();
            console.clear();
            toolWindow.getContentManager().setSelectedContent(content);
            return console;
        }
        console = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
        Content newContent = toolWindow.getContentManager().getFactory().createContent(console.getComponent(), TITLE, false);
        toolWindow.getContentManager().addContent(newContent);
        toolWindow.getContentManager().setSelectedContent(newContent);
        return console;
    }

    private void runInTerminal(String rootPath, String script, List<String> parameters) throws IOException {
        String command = toBashCommand(rootPath, script, parameters);
        TerminalView terminalView = TerminalView.getInstance(project);
        ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID);
        if (window == null) {
            throw new DeployPluginException("Terminal tool window unavailable.");
        }
        window.activate(null);
        ContentManager contentManager = window.getContentManager();
        Content content = contentManager.findContent(TITLE);
        if (content == null) {
            ShellTerminalWidget terminal = terminalView.createLocalShellWidget(project.getBasePath(), TITLE);
            terminal.executeCommand(command);
            return;
        }
        Pair<Content, ShellTerminalWidget> pair = getSuitableProcess(content);
        if (pair == null) {
            throw new DeployPluginException("已有终端在执行命令，请稍后重试。");
        }
        pair.first.setDisplayName(TITLE);
        contentManager.setSelectedContent(pair.first);
        pair.second.executeCommand(command);
    }

    private String toBashCommand(String rootPath, String script, List<String> parameters) {
        if (SystemUtils.IS_OS_WINDOWS) {
            String normalizedRootPath = rootPath.replace("\\", "/");
            String normalizedScriptPath = script.replace("\\", "/");
            String bashExe = ZeroGitDeploySetting.getGitHome() + "\\bin\\bash.exe";

            StringBuilder scriptCmd = new StringBuilder()
                    .append("\"").append(normalizedScriptPath.replace("\"", "\\\"")).append("\"");
            for (String p : parameters) {
                scriptCmd.append(" ").append(quote(p, false));
            }

            String bashActualCommand = "cd " + quote(normalizedRootPath, false) + " && " + scriptCmd;
            StringBuilder sb = new StringBuilder()
                    .append(quote(bashExe, true));
            if (ZeroGitDeploySetting.isDebug()) {
                sb.append(" -x");
            }
            sb.append(" -c ").append(quote(bashActualCommand, true));
            return sb.toString();
        }

        StringBuilder sb = new StringBuilder(buildCdCommand(rootPath)).append(" && ").append("bash");
        if (ZeroGitDeploySetting.isDebug()) {
            sb.append(" -x");
        }
        sb.append(" ").append(quote(script, false));
        for (String p : parameters) {
            sb.append(" ").append(quote(p, false));
        }
        return sb.toString();
    }

    private String buildConfirmMessage(String cmd, String rootPath, String script, List<String> params) {
        return "命令: " + cmd + "\n工作目录: " + rootPath + "\n脚本: " + script + "\n参数: " + String.join(" ", params) + "\n\n确认执行？";
    }

    private List<String> listLocalFeatureBranches(String rootPath, String groupName) throws Exception {
        ExecuteResult result = execGitArgs(rootPath,
                "for-each-ref",
                "--format=%(refname:short)",
                "refs/heads/feature/" + groupName + "/*");
        List<String> branches = splitLines(result.getResult());
        branches.sort((o1, o2) -> Integer.compare(extractFeatureOrder(o2), extractFeatureOrder(o1)));
        return branches;
    }

    private void gitFetchOriginPrune(String rootPath) throws Exception {
        execGitArgs(rootPath, "fetch", "origin", "--prune");
    }

    private List<String> listReleaseBranches(String rootPath, String groupName) throws Exception {
        return listReleaseBranches(rootPath, groupName, true, false);
    }

    private List<String> listReleaseBranches(String rootPath, String groupName, boolean includeLocal) throws Exception {
        return listReleaseBranches(rootPath, groupName, includeLocal, false);
    }

    private List<String> listReleaseBranches(String rootPath, String groupName, boolean includeLocal, boolean skipFetch) throws Exception {
        if (!skipFetch) {
            gitFetchOriginPrune(rootPath);
        }
        List<String> args = new ArrayList<>();
        args.add("for-each-ref");
        args.add("--format=%(refname:short)");
        if (includeLocal) {
            args.add("refs/heads/release/" + groupName + "/*");
        }
        args.add("refs/remotes/origin/release/" + groupName + "/*");
        ExecuteResult result = execGitArgs(rootPath, args.toArray(new String[0]));
        return sortBySemverDesc(uniqueNormalizedBranches(splitLines(result.getResult()), "origin/"));
    }

    private List<String> listAllReleaseBranches(String rootPath, boolean includeLocal) throws Exception {
        return listAllReleaseBranches(rootPath, includeLocal, false);
    }

    private List<String> listAllReleaseBranches(String rootPath, boolean includeLocal, boolean skipFetch) throws Exception {
        if (!skipFetch) {
            gitFetchOriginPrune(rootPath);
        }
        List<String> args = new ArrayList<>();
        args.add("for-each-ref");
        args.add("--format=%(refname:short)");
        if (includeLocal) {
            args.add("refs/heads/release/*/*");
        }
        args.add("refs/remotes/origin/release/*/*");
        ExecuteResult result = execGitArgs(rootPath, args.toArray(new String[0]));
        return sortBySemverDesc(uniqueNormalizedBranches(splitLines(result.getResult()), "origin/"));
    }

    private List<String> listHotfixBranches(String rootPath, String groupName) throws Exception {
        return listHotfixBranches(rootPath, groupName, true, false);
    }

    private List<String> listHotfixBranches(String rootPath, String groupName, boolean includeLocal) throws Exception {
        return listHotfixBranches(rootPath, groupName, includeLocal, false);
    }

    private List<String> listHotfixBranches(String rootPath, String groupName, boolean includeLocal, boolean skipFetch) throws Exception {
        if (!skipFetch) {
            gitFetchOriginPrune(rootPath);
        }
        List<String> args = new ArrayList<>();
        args.add("for-each-ref");
        args.add("--format=%(refname:short)");
        if (includeLocal) {
            args.add("refs/heads/hotfix/" + groupName + "/*");
        }
        args.add("refs/remotes/origin/hotfix/" + groupName + "/*");
        ExecuteResult result = execGitArgs(rootPath, args.toArray(new String[0]));
        return sortBySemverDesc(uniqueNormalizedBranches(splitLines(result.getResult()), "origin/"));
    }

    private List<String> listAllHotfixBranches(String rootPath, boolean includeLocal) throws Exception {
        return listAllHotfixBranches(rootPath, includeLocal, false);
    }

    private List<String> listAllHotfixBranches(String rootPath, boolean includeLocal, boolean skipFetch) throws Exception {
        if (!skipFetch) {
            gitFetchOriginPrune(rootPath);
        }
        List<String> args = new ArrayList<>();
        args.add("for-each-ref");
        args.add("--format=%(refname:short)");
        if (includeLocal) {
            args.add("refs/heads/hotfix/*/*");
        }
        args.add("refs/remotes/origin/hotfix/*/*");
        ExecuteResult result = execGitArgs(rootPath, args.toArray(new String[0]));
        return sortBySemverDesc(uniqueNormalizedBranches(splitLines(result.getResult()), "origin/"));
    }

    private @Nullable HotfixBaseTagInfo getLatestRemoteHotfixBaseTag(String rootPath, String groupName) throws Exception {
        ExecuteResult result = execGitArgs(rootPath, "ls-remote", "--tags", "--refs", "origin");
        // 兼容两种 tag 形态：
        // 1) vX.Y.Z（由 FinishRelease.sh / FinishHotfix 创建）
        // 2) release|hotfix/<group>/X.Y.Z-YYYYMMDDHHmm（历史/扩展形态）
        // 兼容 git ls-remote 对注释 tag 的 peeled 条目：vX.Y.Z^{}
        Pattern semverOnlyTagPattern = Pattern.compile("^v?(\\d+\\.\\d+\\.\\d+)(\\^\\{\\})?$");
        // 为了满足“按最后三位数字递增”的需求：历史 tag 也按全局最大版本取，不限制 groupName
        Pattern tagPattern = Pattern.compile("^(release|hotfix)/[^/]+/(\\d+\\.\\d+\\.\\d+)-(\\d{12})(\\^\\{\\})?$");
        HotfixBaseTagInfo latest = null;
        for (String line : splitLines(result.getResult())) {
            String[] parts = line.split("\\s+");
            if (parts.length < 2) {
                continue;
            }
            String refName = parts[1];
            if (!refName.startsWith("refs/tags/")) {
                continue;
            }
            String tagName = StringUtils.removeStart(refName, "refs/tags/");
            // vX.Y.Z
            Matcher semverOnlyMatcher = semverOnlyTagPattern.matcher(tagName);
            if (semverOnlyMatcher.matches()) {
                String version = semverOnlyMatcher.group(1);
                String timestamp = "";
                if (latest == null
                        || compareSemver(version, latest.version) > 0
                        || (compareSemver(version, latest.version) == 0 && timestamp.compareTo(latest.timestamp) > 0)) {
                    latest = new HotfixBaseTagInfo(tagName, version, timestamp);
                }
                continue;
            }

            // release|hotfix/<group>/X.Y.Z-YYYYMMDDHHmm
            Matcher matcher = tagPattern.matcher(tagName);
            if (!matcher.matches()) continue;

            String version = matcher.group(2);
            String timestamp = matcher.group(3);
            if (latest == null
                    || compareSemver(version, latest.version) > 0
                    || (compareSemver(version, latest.version) == 0 && timestamp.compareTo(latest.timestamp) > 0)) {
                latest = new HotfixBaseTagInfo(tagName, version, timestamp);
            }
        }
        return latest;
    }

    private String findNextAvailableVersion(String baseVersion, List<String> releases, List<String> hotfixes) {
        return findNextAvailableVersion(baseVersion, releases, hotfixes, this::nextMinor);
    }

    private String findNextAvailableVersion(String baseVersion, List<String> releases, List<String> hotfixes, UnaryOperator<String> bump) {
        List<String> existingVersions = new ArrayList<>();
        existingVersions.addAll(extractVersions(releases));
        existingVersions.addAll(extractVersions(hotfixes));
        String candidate = baseVersion;
        while (existingVersions.contains(candidate)) {
            candidate = bump.apply(candidate);
        }
        return candidate;
    }

    private String getMaxSemverVersion(String tagVersion, List<String> releases, List<String> hotfixes) {
        List<String> versions = new ArrayList<>();
        if (SEMVER_PATTERN.matcher(StringUtils.defaultString(tagVersion)).matches()) {
            versions.add(tagVersion);
        }
        versions.addAll(extractVersions(releases));
        versions.addAll(extractVersions(hotfixes));
        if (versions.isEmpty()) {
            return "1.0.0";
        }
        versions.sort(this::compareSemver);
        return versions.get(versions.size() - 1);
    }

    private void ensureSemver(String version, String error) {
        if (!SEMVER_PATTERN.matcher(version).matches()) {
            throw new DeployPluginException(error);
        }
    }

    private void ensureVersionNotExists(String version, List<String> branches, String type) {
        for (String branch : branches) {
            if (version.equals(extractVersion(branch))) {
                throw new DeployPluginException("版本 " + version + " 已存在于 " + type + " 分支中。");
            }
        }
    }

    private String chooseBranch(String msg, String title, List<String> branches) {
        return Messages.showEditableChooseDialog(msg, title, null, branches.toArray(new String[0]), branches.get(0), null);
    }

    private boolean acknowledgeFinishUsageNotice(String title) {
        return Messages.showYesNoDialog(
            "只有 Maintainer 才有权限，请确认你有 Maintainer 权限？\n\n此功能仅限于解决CICD自动化merge代码时出现冲突的场景。解决完冲突后，再到项目的Pipeline里面重新执行对应的job即可。",
            title,
            Messages.getQuestionIcon()
        ) == Messages.YES;
    }

    private boolean yes(String message, String title) {
        return Messages.showYesNoDialog(message, title, Messages.getQuestionIcon()) == Messages.YES;
    }

    private static boolean pomXmlContainsSnapshot(String rootPath) {
        File pom = new File(rootPath, "pom.xml");
        if (!pom.isFile()) {
            return false;
        }
        try {
            String content = new String(Files.readAllBytes(pom.toPath()), StandardCharsets.UTF_8);
            return content.contains("-SNAPSHOT");
        } catch (IOException e) {
            return false;
        }
    }

    /** Start New Release / Hotfix：仓库根存在 pom.xml 且含 -SNAPSHOT 时需用户确认，取消则中断。 */
    private boolean confirmPomSnapshotIfPresent(String rootPath) {
        if (!pomXmlContainsSnapshot(rootPath)) {
            return true;
        }
        int result = Messages.showDialog(
                project,
                "pom.xml中有SNAPSHOT版本依赖，请确认。",
                TITLE,
                new String[]{"已确认，继续", "取消"},
                0,
                Messages.getWarningIcon());
        return result == 0;
    }

    private InputValidator nonEmptyValidator() {
        return new InputValidator() {
            @Override
            public boolean checkInput(@NlsSafe String inputString) {
                return StringUtils.isNotBlank(inputString);
            }

            @Override
            public boolean canClose(@NlsSafe String inputString) {
                return StringUtils.isNotBlank(inputString);
            }
        };
    }

    private ExecuteResult execGitArgs(String rootPath, String... args) throws Exception {
        List<String> parameters = Arrays.asList(args);
        debugLog("execute git command", "git " + String.join(" ", parameters));
        ExecuteResult result = DeployCmdExecuter.execDirect(rootPath, "git", parameters);
        if (result.getCode() != 0) {
            throw new DeployPluginException("Git 命令执行失败，command=git " + String.join(" ", parameters) + "，exitCode=" + result.getCode() + "，详情：" +
                    StringUtils.defaultString(result.getResult(), "(no output)"));
        }
        return result;
    }

    /** 与 GenCommitMessage.sh 一致：exit 0 表示无暂存差异，exit 1 表示有暂存差异。 */
    private boolean gitRepoHasStagedChanges(String rootPath) throws Exception {
        ExecuteResult result = DeployCmdExecuter.execDirect(rootPath, "git", Arrays.asList("diff", "--cached", "--quiet"));
        int code = result.getCode();
        if (code == 0) {
            return false;
        }
        if (code == 1) {
            return true;
        }
        throw new DeployPluginException("Git 命令执行失败，command=git diff --cached --quiet，exitCode=" + code + "，详情：" +
                StringUtils.defaultString(result.getResult(), "(no output)"));
    }

    private String getCurrentBranch(String rootPath) throws Exception {
        ExecuteResult result = execGitArgs(rootPath, "rev-parse", "--abbrev-ref", "HEAD");
        String branch = StringUtils.trim(result.getResult());
        if (StringUtils.isBlank(branch) || "HEAD".equals(branch)) {
            throw new DeployPluginException("当前分支不可用。");
        }
        return branch;
    }

    private List<String> splitLines(String text) {
        List<String> lines = new ArrayList<>();
        if (StringUtils.isBlank(text)) {
            return lines;
        }
        for (String line : text.split("\\R")) {
            if (StringUtils.isNotBlank(line)) {
                lines.add(line.trim());
            }
        }
        return lines;
    }

    private List<String> uniqueNormalizedBranches(List<String> source, String removePrefix) {
        List<String> list = new ArrayList<>();
        for (String s : source) {
            String normalized = StringUtils.removeStart(s, removePrefix);
            if (!list.contains(normalized)) {
                list.add(normalized);
            }
        }
        return list;
    }

    private List<String> sortBySemverDesc(List<String> branches) {
        branches.sort((o1, o2) -> compareSemver(extractVersion(o2), extractVersion(o1)));
        return branches;
    }

    private List<String> extractVersions(List<String> branches) {
        List<String> versions = new ArrayList<>();
        for (String b : branches) {
            String version = extractVersion(b);
            if (SEMVER_PATTERN.matcher(version).matches()) {
                versions.add(version);
            }
        }
        return versions;
    }

    private String extractVersion(String branch) {
        return StringUtils.substringAfterLast(branch, "/");
    }

    private int compareSemver(String v1, String v2) {
        if (!SEMVER_PATTERN.matcher(v1).matches() || !SEMVER_PATTERN.matcher(v2).matches()) {
            return v1.compareTo(v2);
        }
        String[] s1 = v1.split("\\.");
        String[] s2 = v2.split("\\.");
        for (int i = 0; i < 3; i++) {
            int diff = Integer.parseInt(s1[i]) - Integer.parseInt(s2[i]);
            if (diff != 0) {
                return diff;
            }
        }
        return 0;
    }

    private String nextMinor(String semver) {
        Matcher matcher = SEMVER_PATTERN.matcher(semver);
        if (!matcher.matches()) {
            return "1.0.0";
        }
        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        // minor 累计，patch 重置为 0
        return major + "." + (minor + 1) + "." + 0;
    }

    private String nextPatch(String semver) {
        Matcher matcher = SEMVER_PATTERN.matcher(semver);
        if (!matcher.matches()) {
            return "1.0.0";
        }
        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        int patch = Integer.parseInt(matcher.group(3));
        return major + "." + minor + "." + (patch + 1);
    }

    private int extractFeatureOrder(String branch) {
        String suffix = StringUtils.substringAfterLast(branch, "/").toLowerCase(Locale.ROOT);
        String number = StringUtils.substringBefore(suffix, "-");
        if (StringUtils.isNumeric(number)) {
            return Integer.parseInt(number);
        }
        return -1;
    }

    private void parseRemainingAndNotify(String output, String type) {
        if (StringUtils.isBlank(output)) {
            return;
        }
        // 只从返回内容中取包含 REMAINING_RELEASES 的那一行进行解析（FinishRelease/FinishHotfix 脚本均输出该行）
        final String key = "REMAINING_RELEASES:";
        String remaining = null;
        for (String line : output.split("\\R")) {
            String text = line.trim();
            if (text.startsWith("+")) {
                continue;
            }
            if (text.startsWith(key)) {
                remaining = StringUtils.trim(StringUtils.substringAfter(text, key));
                break;
            }
        }
        if (StringUtils.isNotBlank(remaining) && !"none".equalsIgnoreCase(remaining)) {
            Messages.showWarningDialog("目前有进行中的分支：" + remaining + "，请评估是否需要重新测试。", "ZeroGit");
        }
    }

    private @Nullable Pair<Content, ShellTerminalWidget> getSuitableProcess(@NotNull Content content) {
        JBTerminalWidget widget = TerminalView.getWidgetByContent(content);
        if (!(widget instanceof ShellTerminalWidget)) {
            return null;
        }
        ShellTerminalWidget shellTerminalWidget = (ShellTerminalWidget) widget;
        if (!shellTerminalWidget.getTypedShellCommand().isEmpty() || shellTerminalWidget.hasRunningCommands()) {
            return null;
        }
        return new Pair<>(content, shellTerminalWidget);
    }

    private String buildCdCommand(String path) {
        if (SystemUtils.IS_OS_WINDOWS) {
            return "cd /d " + quote(path, true);
        }
        return "cd " + quote(path, false);
    }

    private String quote(String text, boolean windows) {
        if (windows) {
            return "\"" + text.replace("\"", "\\\"") + "\"";
        }
        return "'" + text.replace("'", "'\\''") + "'";
    }

    private void debugLog(String message, String payload) {
        if (!ZeroGitDeploySetting.isDebug()) {
            return;
        }
        if (StringUtils.isBlank(payload)) {
            LOG.info("[zerofinance-git][debug] " + message);
            return;
        }
        LOG.info("[zerofinance-git][debug] " + message + " | " + payload);
    }
}
