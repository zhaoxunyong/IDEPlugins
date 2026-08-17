package com.zerofinance.zerogit.eclipse.actions;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;
import org.eclipse.ui.handlers.HandlerUtil;

import com.zerofinance.zerogit.eclipse.exec.BashCommandBuilder;
import com.zerofinance.zerogit.eclipse.exec.CommandRequest;
import com.zerofinance.zerogit.eclipse.exec.CommandResult;
import com.zerofinance.zerogit.eclipse.exec.ScriptResolver;
import com.zerofinance.zerogit.eclipse.exec.ZeroGitCommandRunner;
import com.zerofinance.zerogit.eclipse.git.GitVersionChecker;
import com.zerofinance.zerogit.eclipse.flow.PomSnapshotSupport;
import com.zerofinance.zerogit.eclipse.flow.ZeroGitFlowService;
import com.zerofinance.zerogit.eclipse.git.GitRepositoryService;
import com.zerofinance.zerogit.eclipse.settings.ZeroGitSettings;
import com.zerofinance.zerogit.eclipse.ui.UserInteraction;

public abstract class AbstractZeroGitHandler extends AbstractHandler {
    private static final String CONSOLE_NAME = "ZeroGit";
    private static final Pattern POM_VERSION_PATTERN = Pattern.compile("<version>\\s*([^<\\s]+)\\s*</version>");

    private final ZeroGitFlowService flowService;
    private final UserInteraction userInteraction;
    private final GitRepositoryService gitRepositoryService;
    private final GitVersionChecker gitVersionChecker;
    private final ZeroGitCommandRunner commandRunner;

    protected AbstractZeroGitHandler() {
        this.flowService = new ZeroGitFlowService();
        this.userInteraction = new UserInteraction();
        this.gitRepositoryService = new GitRepositoryService();
        this.gitVersionChecker = new GitVersionChecker(this.gitRepositoryService);
        this.commandRunner = new ZeroGitCommandRunner(
                new ScriptResolver(new File(System.getProperty("java.io.tmpdir"), "zerogit-cache")),
                new BashCommandBuilder(false));
    }

    protected ZeroGitFlowService flowService() {
        return flowService;
    }

    protected UserInteraction ui() {
        return userInteraction;
    }

    protected GitRepositoryService gitRepositoryService() {
        return gitRepositoryService;
    }

    protected void ensureGitVersionSupported(String repoRoot) throws ExecutionException {
        try {
            gitVersionChecker.ensureSupportedVersion(repoRoot);
        } catch (Exception e) {
            throw new ExecutionException(StringUtils.defaultIfEmpty(e.getMessage(), "Failed to inspect Git version."), e);
        }
    }

    protected Shell shell(ExecutionEvent event) {
        return HandlerUtil.getActiveShell(event);
    }

    protected IProject requireProject(ExecutionEvent event) throws ExecutionException {
        IResource resource = resolveSelectedResource(event);
        if (resource != null) {
            return resource.getProject();
        }

        IEditorInput editorInput = HandlerUtil.getActiveEditorInput(event);
        if (editorInput != null) {
            IFile editorFile = (IFile) editorInput.getAdapter(IFile.class);
            if (editorFile != null) {
                return editorFile.getProject();
            }
            IResource editorResource = (IResource) editorInput.getAdapter(IResource.class);
            if (editorResource != null) {
                return editorResource.getProject();
            }
        }

        throw new ExecutionException("No project or resource selected.");
    }

    protected String requireRepositoryRoot(ExecutionEvent event) throws ExecutionException {
        return findRepositoryRoot(requireContextPath(event));
    }

    protected String requireGroupSelection(Shell shell) throws ExecutionException {
        List<String> groups = ZeroGitSettings.getGroups();
        String selected = ui().chooseGroup(shell, groups, ZeroGitSettings.getDefaultGroup());
        if (StringUtils.isBlank(selected)) {
            throw new ExecutionException("Please select a group, task aborted.");
        }
        return selected;
    }

    protected CommandRequest buildRequest(String repoRoot, String scriptName, List<String> args) {
        return new CommandRequest(
                repoRoot,
                scriptName,
                args,
                ZeroGitSettings.isDebugEnabled(),
                ZeroGitSettings.getGitHome());
    }

    protected CommandResult runScriptNow(CommandRequest request) throws ExecutionException {
        try {
            return commandRunner.runScript(request);
        } catch (Exception e) {
            throw new ExecutionException("执行脚本失败。", e);
        }
    }

    /** Start New Release / Hotfix：当前仓库树任意 pom.xml 含 -SNAPSHOT 依赖/插件版本时需用户确认，取消则中断。 */
    protected boolean confirmPomSnapshotIfPresent(Shell shell, String repoRoot) {
        if (!PomSnapshotSupport.containsSnapshot(new File(repoRoot))) {
            return true;
        }
        return ui().confirm(shell, "ZeroGit", "pom.xml中有SNAPSHOT版本依赖，请确认。");
    }

    protected boolean hasStagedChanges(String repoRoot) throws ExecutionException {
        try {
            return gitRepositoryService().hasStagedChanges(repoRoot);
        } catch (Exception e) {
            throw new ExecutionException("Failed to inspect staged changes.", e);
        }
    }

    protected String requireMavenProjectRoot(ExecutionEvent event) throws ExecutionException {
        String repoRoot = requireRepositoryRoot(event);
        String contextPath = requireContextPath(event);
        File current = new File(contextPath);
        if (current.isFile()) {
            current = current.getParentFile();
        }
        File repoDir = new File(repoRoot);
        File matched = null;
        while (current != null) {
            if (hasValidMavenPom(current)) {
                matched = current;
            }
            if (sameFile(current, repoDir)) {
                break;
            }
            current = current.getParentFile();
        }
        if (matched == null) {
            throw new ExecutionException(
                    "在当前选择目录及其上级目录中未找到有效的 Maven 项目（缺少可用 pom.xml）。请先选择子项目目录后重试。");
        }
        return matched.getAbsolutePath();
    }

    protected String readPomVersion(String projectRoot) {
        File pomFile = new File(projectRoot, "pom.xml");
        if (!pomFile.exists() || !pomFile.isFile()) {
            return null;
        }
        try {
            String content = new String(Files.readAllBytes(pomFile.toPath()), StandardCharsets.UTF_8);
            String noComments = content.replaceAll("(?s)<!--.*?-->", "");
            String noParentBlock = noComments.replaceAll("(?s)<parent>.*?</parent>", "");
            Matcher matcher = POM_VERSION_PATTERN.matcher(noParentBlock);
            return matcher.find() ? StringUtils.trimToNull(matcher.group(1)) : null;
        } catch (Exception e) {
            return null;
        }
    }

    protected void runScriptJob(final Shell shell, final String title, final IProject project,
            final CommandRequest request, final boolean runGitCheck) {
        runScriptJob(shell, title, project, request, runGitCheck, null);
    }

    protected void runScriptJob(final Shell shell, final String title, final IProject project,
            final CommandRequest request, final boolean runGitCheck, final SuccessCallback successCallback) {
        final MessageConsoleStream console = openConsole(true);
        Job job = new Job("ZeroGit: " + title) {
            @Override
            protected IStatus run(org.eclipse.core.runtime.IProgressMonitor monitor) {
                try {
                    console.println("[" + title + "] repo: " + request.getRepoRoot());
                    if (runGitCheck) {
                        if (ZeroGitSettings.isGitVersionCheckEnabled()) {
                            ensureGitVersionSupported(request.getRepoRoot());
                        }
                        CommandResult gitCheckResult = commandRunner.runScript(
                                buildRequest(request.getRepoRoot(), "gitCheck.sh", Collections.<String>emptyList()));
                        writeOutput(console, gitCheckResult);
                        if (!gitCheckResult.isSuccess()) {
                            throw new ExecutionException(buildErrorMessage("gitCheck failed", gitCheckResult));
                        }
                    }

                    CommandResult result = commandRunner.runScript(request);
                    writeOutput(console, result);
                    if (!result.isSuccess()) {
                        throw new ExecutionException(buildErrorMessage(title + " failed", result));
                    }

                    commandRunner.refreshProject(project);
                    showSuccess(shell, title, result, successCallback);
                    return Status.OK_STATUS;
                } catch (final Exception e) {
                    Display.getDefault().asyncExec(new Runnable() {
                        @Override
                        public void run() {
                            ui().showError(shell, "ZeroGit", e.getMessage());
                        }
                    });
                    return new Status(IStatus.ERROR, "com.zerofinance.zerogit.eclipse", e.getMessage(), e);
                }
            }
        };
        job.schedule();
    }

    protected void runRawCommandJob(final Shell shell, final String title, final IProject project,
            final String repoRoot, final String commandText, final SuccessCallback successCallback) {
        final MessageConsoleStream console = openConsole(true);
        Job job = new Job("ZeroGit: " + title) {
            @Override
            protected IStatus run(org.eclipse.core.runtime.IProgressMonitor monitor) {
                try {
                    console.println("[" + title + "] repo: " + repoRoot);
                    String[] commandParts = new BashCommandBuilder(false).buildShellCommand(
                            ZeroGitSettings.isDebugEnabled(),
                            ZeroGitSettings.getGitHome(),
                            repoRoot,
                            commandText);
                    CommandResult result = commandRunner.runRawCommand(repoRoot, commandParts);
                    writeOutput(console, result);
                    if (!result.isSuccess()) {
                        throw new ExecutionException(buildErrorMessage(title + " failed", result));
                    }

                    commandRunner.refreshProject(project);
                    showSuccess(shell, title, result, successCallback);
                    return Status.OK_STATUS;
                } catch (final Exception e) {
                    Display.getDefault().asyncExec(new Runnable() {
                        @Override
                        public void run() {
                            ui().showError(shell, "ZeroGit", e.getMessage());
                        }
                    });
                    return new Status(IStatus.ERROR, "com.zerofinance.zerogit.eclipse", e.getMessage(), e);
                }
            }
        };
        job.schedule();
    }

    protected interface SuccessCallback {
        void onSuccess(Shell shell, String title, CommandResult result);
    }

    private IResource resolveSelectedResource(ExecutionEvent event) {
        ISelection selection = HandlerUtil.getCurrentSelection(event);
        if (!(selection instanceof IStructuredSelection)) {
            return null;
        }
        Object firstElement = ((IStructuredSelection) selection).getFirstElement();
        if (firstElement instanceof IResource) {
            return (IResource) firstElement;
        }
        if (firstElement instanceof IAdaptable) {
            return (IResource) ((IAdaptable) firstElement).getAdapter(IResource.class);
        }
        return null;
    }

    private String findRepositoryRoot(String path) throws ExecutionException {
        try {
            return gitRepositoryService.findRepositoryRoot(path);
        } catch (Exception e) {
            throw new ExecutionException("Cannot resolve git repository root from: " + path, e);
        }
    }

    private String requireContextPath(ExecutionEvent event) throws ExecutionException {
        IResource resource = resolveSelectedResource(event);
        if (resource != null && resource.getLocation() != null) {
            return resource.getLocation().toFile().getAbsolutePath();
        }

        IEditorInput editorInput = HandlerUtil.getActiveEditorInput(event);
        if (editorInput != null) {
            IFile editorFile = (IFile) editorInput.getAdapter(IFile.class);
            if (editorFile != null && editorFile.getLocation() != null) {
                return editorFile.getLocation().toFile().getAbsolutePath();
            }
            IResource editorResource = (IResource) editorInput.getAdapter(IResource.class);
            if (editorResource != null && editorResource.getLocation() != null) {
                return editorResource.getLocation().toFile().getAbsolutePath();
            }
        }

        IProject project = requireProject(event);
        if (project.getLocation() == null) {
            throw new ExecutionException("Cannot resolve project filesystem path.");
        }
        return project.getLocation().toFile().getAbsolutePath();
    }

    private MessageConsoleStream openConsole(boolean clear) {
        MessageConsole console = findConsole(CONSOLE_NAME);
        if (clear) {
            console.clearConsole();
        }
        console.activate();
        return console.newMessageStream();
    }

    private MessageConsole findConsole(String name) {
        ConsolePlugin plugin = ConsolePlugin.getDefault();
        IConsoleManager manager = plugin.getConsoleManager();
        for (IConsole console : manager.getConsoles()) {
            if (name.equals(console.getName())) {
                return (MessageConsole) console;
            }
        }
        MessageConsole created = new MessageConsole(name, null);
        manager.addConsoles(new IConsole[] {created});
        return created;
    }

    private void writeOutput(MessageConsoleStream console, CommandResult result) {
        if (result == null || StringUtils.isBlank(result.getOutput())) {
            return;
        }
        console.println(result.getOutput());
    }

    private String buildErrorMessage(String fallback, CommandResult result) {
        String output = result == null ? "" : StringUtils.trimToEmpty(result.getOutput());
        if (StringUtils.isNotBlank(output)) {
            return output;
        }
        return fallback;
    }

    private boolean hasValidMavenPom(File directory) {
        if (directory == null) {
            return false;
        }
        File pomFile = new File(directory, "pom.xml");
        if (!pomFile.exists() || !pomFile.isFile()) {
            return false;
        }
        try {
            String content = new String(Files.readAllBytes(pomFile.toPath()), StandardCharsets.UTF_8);
            return content.contains("<project");
        } catch (Exception e) {
            return false;
        }
    }

    private boolean sameFile(File left, File right) {
        try {
            return left.getCanonicalFile().equals(right.getCanonicalFile());
        } catch (Exception e) {
            return left.equals(right);
        }
    }

    private void showSuccess(final Shell shell, final String title, final CommandResult result,
            final SuccessCallback successCallback) {
        Display.getDefault().asyncExec(new Runnable() {
            @Override
            public void run() {
                if (successCallback != null) {
                    successCallback.onSuccess(shell, title, result);
                } else {
                    ui().showInfo(shell, "ZeroGit", title + " completed.");
                }
            }
        });
    }
}
