package com.zerofinance.zerogit.eclipse.tests.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

import org.junit.Test;

import com.zerofinance.zerogit.eclipse.git.GitRepositoryService;
import com.zerofinance.zerogit.eclipse.git.VersionService.HotfixBaseTagInfo;

public class GitRepositoryServiceTest {

    @Test
    public void listAllReleaseBranchesFetchesOriginWhenRemoteTrackingBranchesAreMissing() throws Exception {
        File remoteDir = Files.createTempDirectory("zerogit-release-remote").toFile();
        initRepository(remoteDir);
        writeFile(remoteDir, "README.md", "remote\n");
        git(remoteDir, "add", "README.md");
        git(remoteDir, "commit", "-m", "remote init");
        git(remoteDir, "checkout", "-b", "release/a/1.5.0");

        File localDir = createLocalRepositoryWithOrigin(remoteDir);

        GitRepositoryService service = new GitRepositoryService();

        assertEquals(Arrays.asList("release/a/1.5.0"), service.listAllReleaseBranches(localDir.getAbsolutePath(), false));
    }

    @Test
    public void listAllHotfixBranchesFetchesOriginWhenRemoteTrackingBranchesAreMissing() throws Exception {
        File remoteDir = Files.createTempDirectory("zerogit-hotfix-remote").toFile();
        initRepository(remoteDir);
        writeFile(remoteDir, "README.md", "remote\n");
        git(remoteDir, "add", "README.md");
        git(remoteDir, "commit", "-m", "remote init");
        git(remoteDir, "checkout", "-b", "hotfix/a/1.4.2");

        File localDir = createLocalRepositoryWithOrigin(remoteDir);

        GitRepositoryService service = new GitRepositoryService();

        assertEquals(Arrays.asList("hotfix/a/1.4.2"), service.listAllHotfixBranches(localDir.getAbsolutePath(), false));
    }

    @Test
    public void latestRemoteHotfixBaseTagUsesConfiguredOriginFromRepository() throws Exception {
        File remoteDir = Files.createTempDirectory("zerogit-remote").toFile();
        initRepository(remoteDir);
        writeFile(remoteDir, "README.md", "remote\n");
        git(remoteDir, "add", "README.md");
        git(remoteDir, "commit", "-m", "remote init");
        git(remoteDir, "tag", "release/a/1.4.0-202606041200");
        git(remoteDir, "tag", "hotfix/a/1.4.1-202606041530");
        git(remoteDir, "tag", "hotfix/a/1.4.1-202606041630");

        File localDir = createLocalRepositoryWithOrigin(remoteDir);

        GitRepositoryService service = new GitRepositoryService();
        HotfixBaseTagInfo latest = service.getLatestRemoteHotfixBaseTag(localDir.getAbsolutePath());

        assertNotNull(latest);
        assertEquals("hotfix/a/1.4.1-202606041630", latest.getTagName());
    }

    @Test
    public void detectsWhetherRepositoryHasStagedChanges() throws Exception {
        File repoDir = Files.createTempDirectory("zerogit-staged").toFile();
        initRepository(repoDir);
        writeFile(repoDir, "README.md", "init\n");
        git(repoDir, "add", "README.md");
        git(repoDir, "commit", "-m", "init");

        GitRepositoryService service = new GitRepositoryService();
        assertFalse(service.hasStagedChanges(repoDir.getAbsolutePath()));

        writeFile(repoDir, "README.md", "changed\n");
        assertFalse(service.hasStagedChanges(repoDir.getAbsolutePath()));

        git(repoDir, "add", "README.md");
        assertTrue(service.hasStagedChanges(repoDir.getAbsolutePath()));
    }

    private File createLocalRepositoryWithOrigin(File remoteDir) throws Exception {
        File localDir = Files.createTempDirectory("zerogit-local").toFile();
        initRepository(localDir);
        writeFile(localDir, "README.md", "test\n");
        git(localDir, "add", "README.md");
        git(localDir, "commit", "-m", "init");
        git(localDir, "remote", "add", "origin", remoteDir.getAbsolutePath());
        git(localDir, "config", "remote.origin.fetch", "+refs/heads/*:refs/remotes/origin/*");
        return localDir;
    }

    private void initRepository(File directory) throws Exception {
        git(directory, "init");
        git(directory, "config", "user.name", "ZeroGit Test");
        git(directory, "config", "user.email", "zerogit@example.com");
    }

    private void writeFile(File directory, String relativePath, String content) throws IOException {
        Files.write(new File(directory, relativePath).toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private void git(File workingDirectory, String... args) throws Exception {
        Process process = new ProcessBuilder(buildGitCommand(args))
                .directory(workingDirectory)
                .redirectErrorStream(true)
                .start();
        String output = readOutput(process.getInputStream());
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new AssertionError("git command failed (" + exitCode + "): " + output);
        }
    }

    private String[] buildGitCommand(String... args) {
        String[] command = new String[args.length + 1];
        command[0] = "git";
        System.arraycopy(args, 0, command, 1, args.length);
        return command;
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
}
