package com.zerofinance.zerogit.eclipse.exec;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.PumpStreamHandler;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;

import com.zerofinance.zerogit.eclipse.settings.ZeroGitSettings;

public class ZeroGitCommandRunner {
    private final ScriptResolver scriptResolver;
    private final BashCommandBuilder bashCommandBuilder;

    public ZeroGitCommandRunner(ScriptResolver scriptResolver, BashCommandBuilder bashCommandBuilder) {
        this.scriptResolver = scriptResolver;
        this.bashCommandBuilder = bashCommandBuilder;
    }

    public CommandResult runScript(CommandRequest request) throws Exception {
        scriptResolver.clearCache();
        String scriptPath = scriptResolver.resolve(
                request.getRepoRoot(),
                request.getScriptName(),
                ZeroGitSettings.getScriptUrl());
        String[] commandParts = bashCommandBuilder.build(
                request.isDebug(),
                request.getGitHome(),
                scriptPath,
                request.getArgs());
        return execute(request.getRepoRoot(), commandParts);
    }

    public CommandResult runRawCommand(String repoRoot, String... commandParts) throws Exception {
        return execute(repoRoot, commandParts);
    }

    public void refreshProject(IProject project) throws Exception {
        if (project != null) {
            project.refreshLocal(IResource.DEPTH_INFINITE, null);
        }
    }

    private CommandResult execute(String repoRoot, String[] commandParts) throws Exception {
        DefaultExecutor executor = new DefaultExecutor();
        executor.setWorkingDirectory(new File(repoRoot));

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
        executor.setStreamHandler(streamHandler);

        int exitCode;
        try {
            exitCode = executor.execute(toCommandLine(commandParts));
        } catch (ExecuteException e) {
            exitCode = e.getExitValue();
        }
        String output = outputStream.toString(StandardCharsets.UTF_8.name());
        return new CommandResult(exitCode, output);
    }

    private CommandLine toCommandLine(String[] commandParts) {
        CommandLine commandLine = new CommandLine(commandParts[0]);
        for (int i = 1; i < commandParts.length; i++) {
            commandLine.addArgument(commandParts[i], false);
        }
        return commandLine;
    }
}
