package com.zerofinance.zerogit.eclipse.git;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import com.zerofinance.zerogit.eclipse.exec.BashCommandBuilder;
import com.zerofinance.zerogit.eclipse.settings.ZeroGitSettings;

public class GitRepositoryService {
    private final VersionService versionService;
    private final BashCommandBuilder commandBuilder;

    public GitRepositoryService() {
        this(new VersionService());
    }

    public GitRepositoryService(VersionService versionService) {
        this.versionService = versionService;
        this.commandBuilder = new BashCommandBuilder(false);
    }

    public String findRepositoryRoot(String path) throws IOException {
        File start = new File(StringUtils.trimToEmpty(path));
        File workingDirectory = start.isFile() ? start.getParentFile() : start;
        try {
            return normalizePath(new File(runGit(workingDirectory, "rev-parse", "--show-toplevel")));
        } catch (IOException e) {
            throw new IllegalArgumentException("Making sure this is a git project!", e);
        }
    }

    public String readCurrentBranch(String repoRoot) throws IOException {
        return runGit(new File(repoRoot), "rev-parse", "--abbrev-ref", "HEAD");
    }

    public String readGitVersion(String repoRoot) throws IOException {
        return runGit(new File(repoRoot), "version");
    }

    public List<String> listLocalFeatureBranches(String repoRoot, String group) throws IOException {
        return listBranches(repoRoot, "feature/" + StringUtils.trimToEmpty(group) + "/", true, false);
    }

    public List<String> listReleaseBranches(String repoRoot, String group, boolean includeLocal) throws IOException {
        return versionService.sortBranchesBySemverDesc(
                listBranches(repoRoot, "release/" + StringUtils.trimToEmpty(group) + "/", includeLocal, true));
    }

    public List<String> listAllReleaseBranches(String repoRoot, boolean includeLocal) throws IOException {
        return versionService.sortBranchesBySemverDesc(listBranches(repoRoot, "release/", includeLocal, true));
    }

    public List<String> listHotfixBranches(String repoRoot, String group, boolean includeLocal) throws IOException {
        return versionService.sortBranchesBySemverDesc(
                listBranches(repoRoot, "hotfix/" + StringUtils.trimToEmpty(group) + "/", includeLocal, true));
    }

    public List<String> listAllHotfixBranches(String repoRoot, boolean includeLocal) throws IOException {
        return versionService.sortBranchesBySemverDesc(listBranches(repoRoot, "hotfix/", includeLocal, true));
    }

    public VersionService.HotfixBaseTagInfo getLatestRemoteHotfixBaseTag(String repoRoot) throws IOException {
        List<String> tagNames = new ArrayList<String>();
        for (String line : runGitLines(new File(repoRoot), "ls-remote", "--tags", "--refs", "origin")) {
            String[] parts = line.split("\\s+");
            if (parts.length >= 2) {
                tagNames.add(StringUtils.removeStart(parts[1], "refs/tags/"));
            }
        }
        return versionService.findLatestHotfixBaseTag(tagNames);
    }

    public boolean hasStagedChanges(String repoRoot) throws IOException {
        return !runGitLines(new File(repoRoot), "diff", "--cached", "--name-only").isEmpty();
    }

    private List<String> listBranches(String repoRoot, String branchPrefix, boolean includeLocal, boolean includeRemote)
            throws IOException {
        Set<String> branches = new LinkedHashSet<String>();
        File repoDirectory = new File(repoRoot);
        if (includeRemote) {
            fetchOriginPrune(repoDirectory);
        }
        if (includeLocal) {
            branches.addAll(runGitLines(
                    repoDirectory,
                    "for-each-ref",
                    "--format=%(refname:short)",
                    "refs/heads/" + branchPattern(branchPrefix)));
        }
        if (includeRemote) {
            for (String branch : runGitLines(
                    repoDirectory,
                    "for-each-ref",
                    "--format=%(refname:short)",
                    "refs/remotes/origin/" + branchPattern(branchPrefix))) {
                branches.add(StringUtils.removeStart(branch, "origin/"));
            }
        }
        return new ArrayList<String>(branches);
    }

    private void fetchOriginPrune(File repoDirectory) throws IOException {
        runGit(repoDirectory, "fetch", "origin", "--prune");
    }

    private List<String> runGitLines(File workingDirectory, String... gitArgs) throws IOException {
        List<String> lines = new ArrayList<String>();
        for (String line : runGit(workingDirectory, gitArgs).split("\\R")) {
            String normalized = StringUtils.trimToEmpty(line);
            if (StringUtils.isNotBlank(normalized)) {
                lines.add(normalized);
            }
        }
        return lines;
    }

    private String runGit(File workingDirectory, String... gitArgs) throws IOException {
        String[] command = commandBuilder.buildShellCommand(
                ZeroGitSettings.isDebugEnabled(),
                ZeroGitSettings.getGitHome(),
                workingDirectory.getAbsolutePath(),
                buildGitCommandText(gitArgs));
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        String output = readOutput(process.getInputStream());
        int exitCode = waitFor(process, gitArgs);
        if (exitCode != 0) {
            throw new IOException("git command failed (" + exitCode + "): "
                    + StringUtils.defaultIfEmpty(StringUtils.trimToEmpty(output), joinArgs(gitArgs)));
        }
        return StringUtils.trimToEmpty(output);
    }

    private int waitFor(Process process, String... gitArgs) throws IOException {
        try {
            return process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while running git " + joinArgs(gitArgs), e);
        }
    }

    private String readOutput(InputStream inputStream) throws IOException {
        StringBuilder buffer = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            if (buffer.length() > 0) {
                buffer.append('\n');
            }
            buffer.append(line);
        }
        return buffer.toString();
    }

    private String buildGitCommandText(String... gitArgs) {
        StringBuilder command = new StringBuilder("git");
        for (String gitArg : gitArgs) {
            command.append(' ').append(shellQuote(gitArg));
        }
        return command.toString();
    }

    private String branchPattern(String branchPrefix) {
        if ("release/".equals(branchPrefix) || "hotfix/".equals(branchPrefix)) {
            return branchPrefix + "*/*";
        }
        return branchPrefix + "*";
    }

    private String joinArgs(String... gitArgs) {
        return StringUtils.join(gitArgs, ' ');
    }

    private String shellQuote(String value) {
        return "'" + StringUtils.defaultString(value).replace("'", "'\\''") + "'";
    }

    private String normalizePath(File directory) {
        return directory.getAbsolutePath().replace("\\", "/");
    }
}
