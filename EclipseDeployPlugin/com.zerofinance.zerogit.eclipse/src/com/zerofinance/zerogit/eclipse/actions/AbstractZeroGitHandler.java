package com.zerofinance.zerogit.eclipse.actions;

import java.io.File;
import java.util.Collections;
import java.util.List;

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
import com.zerofinance.zerogit.eclipse.flow.ZeroGitFlowService;
import com.zerofinance.zerogit.eclipse.git.GitRepositoryService;
import com.zerofinance.zerogit.eclipse.settings.ZeroGitSettings;
import com.zerofinance.zerogit.eclipse.ui.UserInteraction;

public abstract class AbstractZeroGitHandler extends AbstractHandler {
    private static final String CONSOLE_NAME = "ZeroGit";

    private final ZeroGitFlowService flowService;
    private final UserInteraction userInteraction;
    private final GitRepositoryService gitRepositoryService;
    private final ZeroGitCommandRunner commandRunner;

    protected AbstractZeroGitHandler() {
        this.flowService = new ZeroGitFlowService();
        this.userInteraction = new UserInteraction();
        this.gitRepositoryService = new GitRepositoryService();
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
        IResource resource = resolveSelectedResource(event);
        if (resource != null && resource.getLocation() != null) {
            return findRepositoryRoot(resource.getLocation().toFile().getAbsolutePath());
        }

        IEditorInput editorInput = HandlerUtil.getActiveEditorInput(event);
        if (editorInput != null) {
            IFile editorFile = (IFile) editorInput.getAdapter(IFile.class);
            if (editorFile != null && editorFile.getLocation() != null) {
                return findRepositoryRoot(editorFile.getLocation().toFile().getAbsolutePath());
            }
        }

        IProject project = requireProject(event);
        if (project.getLocation() == null) {
            throw new ExecutionException("Cannot resolve project filesystem path.");
        }
        return findRepositoryRoot(project.getLocation().toFile().getAbsolutePath());
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

    protected boolean hasStagedChanges(String repoRoot) throws ExecutionException {
        try {
            return gitRepositoryService().hasStagedChanges(repoRoot);
        } catch (Exception e) {
            throw new ExecutionException("Failed to inspect staged changes.", e);
        }
    }

    protected void runScriptJob(final Shell shell, final String title, final IProject project,
            final CommandRequest request, final boolean runGitCheck) {
        final MessageConsoleStream console = openConsole(true);
        Job job = new Job("ZeroGit: " + title) {
            @Override
            protected IStatus run(org.eclipse.core.runtime.IProgressMonitor monitor) {
                try {
                    console.println("[" + title + "] repo: " + request.getRepoRoot());
                    if (runGitCheck) {
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
                    Display.getDefault().asyncExec(new Runnable() {
                        @Override
                        public void run() {
                            ui().showInfo(shell, "ZeroGit", title + " completed.");
                        }
                    });
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
}
