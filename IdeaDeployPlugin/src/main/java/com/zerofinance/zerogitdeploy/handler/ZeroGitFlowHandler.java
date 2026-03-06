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
import com.intellij.openapi.util.text.StringUtil;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("restriction")
public class ZeroGitFlowHandler {
    private static final Pattern FEATURE_SUFFIX_PATTERN = Pattern.compile("^\\d+-\\S.*$");
    private static final Pattern SEMVER_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)$");
    private static final String[] GROUPS = new String[]{"a", "b"};
    private static final String TITLE = "ZeroGit";
    private static final Logger LOG = Logger.getInstance(ZeroGitFlowHandler.class);

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
    }

    public void startNewFeature() throws Exception {
        debugLog("command triggered", "Start New Feature");
        String groupName = requireGroupName();
        String rootPath = getRootPath();
        runGitCheck(rootPath);
        CommandUtils.clearZeroGitScriptCache();
        String script = CommandUtils.processZeroGitScript(rootPath, "StartNewFeature.sh");

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
        confirmAndRunInTerminal("Start New Feature", rootPath, script, Lists.newArrayList(groupName, input));
    }

    public void finishFeature() throws Exception {
        debugLog("command triggered", "Finish Feature");
        String groupName = requireGroupName();
        if (!yes("请确认：是否已在 GitLab 中 MR 到 develop-" + groupName + " 并完成 Merge？\n继续只会删除本地 feature 分支。", "ZeroGit: Finish Feature")) {
            return;
        }
        String rootPath = getRootPath();
        runGitCheck(rootPath);
        CommandUtils.clearZeroGitScriptCache();
        String script = CommandUtils.processZeroGitScript(rootPath, "FinishFeature.sh");

        List<String> branches = listLocalFeatureBranches(rootPath, groupName);
        if (branches.isEmpty()) {
            throw new DeployPluginException("No local feature branch found for group \"" + groupName + "\"");
        }
        String selected = chooseBranch("请选择要结束的 feature 分支", "ZeroGit: Finish Feature", branches);
        if (StringUtils.isBlank(selected)) {
            return;
        }
        confirmAndRunInTerminal("Finish Feature", rootPath, script, Lists.newArrayList(groupName, selected));
    }

    public void rebaseFeature() throws Exception {
        debugLog("command triggered", "Rebase Feature");
        String groupName = requireGroupName();
        String rootPath = getRootPath();
        runGitCheck(rootPath);
        CommandUtils.clearZeroGitScriptCache();
        String script = CommandUtils.processZeroGitScript(rootPath, "RebaseFeature.sh");

        String currentBranch = getCurrentBranch(rootPath);
        String requiredPrefix = "feature/" + groupName + "/";
        if (!currentBranch.startsWith(requiredPrefix)) {
            throw new DeployPluginException("当前分支不是 " + requiredPrefix + " 开头，无法 Rebase Feature。");
        }
        confirmAndRunInTerminal("Rebase Feature", rootPath, script, Lists.newArrayList(groupName, currentBranch));
    }

    public void startNewRelease() throws Exception {
        debugLog("command triggered", "Start New Release");
        String groupName = requireGroupName();
        if (!yes("请确认：是否已执行 Finish Feature 操作？", "ZeroGit: Start New Release")) {
            return;
        }
        String rootPath = getRootPath();
        runGitCheck(rootPath);
        CommandUtils.clearZeroGitScriptCache();
        String script = CommandUtils.processZeroGitScript(rootPath, "StartNewRelease.sh");

        List<String> releases = listReleaseBranches(rootPath, groupName);
        List<String> hotfixes = listHotfixBranches(rootPath, groupName);
        String suggested = suggestNextVersion(releases, hotfixes, listTags(rootPath));
        String prefix = "release/" + groupName + "/";
        String value = Messages.showInputDialog(
                "请输入 Release 分支（SemVer）",
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

        confirmAndRunInTerminal("Start New Release", rootPath, script, Lists.newArrayList(groupName, value));
    }

    public void finishRelease() throws Exception {
        debugLog("command triggered", "Finish Release");
        String groupName = requireGroupName();
        if (!yes("只有 Maintainer 才有权限，请确认你有 Maintainer 权限？", "ZeroGit: Finish Release")) {
            return;
        }
        if (!yes("运维是否已完成上线？", "ZeroGit: Finish Release")) {
            return;
        }
        String rootPath = getRootPath();
        runGitCheck(rootPath);
        CommandUtils.clearZeroGitScriptCache();
        String script = CommandUtils.processZeroGitScript(rootPath, "FinishRelease.sh");
        List<String> releases = listReleaseBranches(rootPath, groupName);
        if (releases.isEmpty()) {
            throw new DeployPluginException("No remote release branch found for group \"" + groupName + "\"");
        }
        String selected = chooseBranch("请选择要结束的 release 分支", "ZeroGit: Finish Release", releases);
        if (StringUtils.isBlank(selected)) {
            return;
        }
        List<String> params = Lists.newArrayList(groupName, selected, String.join(",", GROUPS));
        confirmAndRunSyncAsync("Finish Release", rootPath, script, params, "release");
    }

    public void startNewHotfix() throws Exception {
        debugLog("command triggered", "Start New Hotfix");
        String groupName = requireGroupName();
        String rootPath = getRootPath();
        runGitCheck(rootPath);
        CommandUtils.clearZeroGitScriptCache();
        String script = CommandUtils.processZeroGitScript(rootPath, "StartNewHotfix.sh");

        List<String> releases = listReleaseBranches(rootPath, groupName);
        List<String> hotfixes = listHotfixBranches(rootPath, groupName);
        String suggested = suggestNextVersion(releases, hotfixes, listTags(rootPath));
        String prefix = "hotfix/" + groupName + "/";
        String value = Messages.showInputDialog(
                "请输入 Hotfix 分支（SemVer）",
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

        confirmAndRunInTerminal("Start New Hotfix", rootPath, script, Lists.newArrayList(groupName, value));
    }

    public void finishHotfix() throws Exception {
        debugLog("command triggered", "Finish Hotfix");
        String groupName = requireGroupName();
        if (!yes("只有 Maintainer 才有权限，请确认你有 Maintainer 权限？", "ZeroGit: Finish Hotfix")) {
            return;
        }
        if (!yes("运维是否已完成上线？", "ZeroGit: Finish Hotfix")) {
            return;
        }
        String rootPath = getRootPath();
        runGitCheck(rootPath);
        CommandUtils.clearZeroGitScriptCache();
        String script = CommandUtils.processZeroGitScript(rootPath, "FinishHotfix.sh");
        List<String> hotfixes = listHotfixBranches(rootPath, groupName);
        if (hotfixes.isEmpty()) {
            throw new DeployPluginException("No remote hotfix branch found for group \"" + groupName + "\"");
        }
        String selected = chooseBranch("请选择要结束的 hotfix 分支", "ZeroGit: Finish Hotfix", hotfixes);
        if (StringUtils.isBlank(selected)) {
            return;
        }
        List<String> params = Lists.newArrayList(groupName, selected, String.join(",", GROUPS));
        confirmAndRunSyncAsync("Finish Hotfix", rootPath, script, params, "hotfix");
    }

    private String requireGroupName() {
        String group = ZeroGitDeploySetting.getGroupName();
        if ("a".equals(group) || "b".equals(group)) {
            return group;
        }
        Messages.showWarningDialog("请先配置 groupName 为 a 或 b。", "ZeroGit");
        ShowSettingsUtil.getInstance().showSettingsDialog(project, ZeroGitDeploySetting.class);
        throw new DeployPluginException("请先配置 groupName 为 a 或 b。");
    }

    private String getRootPath() {
        return CommandUtils.getRootProjectPath(modulePath);
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
        StringBuilder sb = new StringBuilder("cd ").append(quote(rootPath)).append(" && ");
        if (SystemUtils.IS_OS_WINDOWS) {
            String bashExe = ZeroGitDeploySetting.getGitHome() + "\\bin\\bash.exe";
            sb.append(quote(bashExe));
        } else {
            sb.append("bash");
        }
        if (ZeroGitDeploySetting.isDebug()) {
            sb.append(" -x");
        }
        sb.append(" ").append(quote(script));
        for (String p : parameters) {
            sb.append(" ").append(quote(p));
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

    private List<String> listReleaseBranches(String rootPath, String groupName) throws Exception {
        execGitArgs(rootPath, "fetch", "origin", "--prune");
        ExecuteResult result = execGitArgs(rootPath,
                "for-each-ref",
                "--format=%(refname:short)",
                "refs/heads/release/" + groupName + "/*",
                "refs/remotes/origin/release/" + groupName + "/*");
        return sortBySemverDesc(uniqueNormalizedBranches(splitLines(result.getResult()), "origin/"));
    }

    private List<String> listHotfixBranches(String rootPath, String groupName) throws Exception {
        execGitArgs(rootPath, "fetch", "origin", "--prune");
        ExecuteResult result = execGitArgs(rootPath,
                "for-each-ref",
                "--format=%(refname:short)",
                "refs/heads/hotfix/" + groupName + "/*",
                "refs/remotes/origin/hotfix/" + groupName + "/*");
        return sortBySemverDesc(uniqueNormalizedBranches(splitLines(result.getResult()), "origin/"));
    }

    private List<String> listTags(String rootPath) throws Exception {
        ExecuteResult result = execGitArgs(rootPath, "ls-remote", "--tags", "--refs", "origin");
        List<String> versions = new ArrayList<>();
        for (String line : splitLines(result.getResult())) {
            String tag = StringUtil.substringAfterLast(line, "/");
            tag = StringUtils.removeStart(tag, "v");
            if (SEMVER_PATTERN.matcher(tag).matches()) {
                versions.add(tag);
            }
        }
        return versions;
    }

    private String suggestNextVersion(List<String> releases, List<String> hotfixes, List<String> tags) {
        List<String> versions = new ArrayList<>();
        versions.addAll(extractVersions(releases));
        versions.addAll(extractVersions(hotfixes));
        versions.addAll(tags);
        if (versions.isEmpty()) {
            return "1.0.0";
        }
        versions.sort(this::compareSemver);
        return nextPatch(versions.get(versions.size() - 1));
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

    private boolean yes(String message, String title) {
        return Messages.showYesNoDialog(message, title, Messages.getQuestionIcon()) == Messages.YES;
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

    private String nextPatch(String semver) {
        Matcher matcher = SEMVER_PATTERN.matcher(semver);
        if (!matcher.matches()) {
            return "1.0.0";
        }
        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        int patch = Integer.parseInt(matcher.group(3)) + 1;
        return major + "." + minor + "." + patch;
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
        String key = "release".equals(type) ? "REMAINING_RELEASES:" : "REMAINING_HOTFIXES:";
        String fallback = "release".equals(type) ? "Remaining release branches:" : "Remaining hotfix branches:";
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
            if (text.startsWith(fallback)) {
                remaining = StringUtils.trim(StringUtils.substringAfter(text, fallback));
                break;
            }
        }
        if (StringUtils.isNotBlank(remaining) && !"none".equalsIgnoreCase(remaining)) {
            Messages.showWarningDialog("目前有进行中的分支：" + remaining + "，请项目经理评估是否需要重新测试。", "ZeroGit");
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

    private String quote(String text) {
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
