package com.zerofinance.zerogit.eclipse.tests.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.StoredConfig;
import org.junit.Test;

import com.zerofinance.zerogit.eclipse.git.GitRepositoryService;
import com.zerofinance.zerogit.eclipse.git.VersionService.HotfixBaseTagInfo;

public class GitRepositoryServiceTest {

    @Test
    public void latestRemoteHotfixBaseTagUsesConfiguredOriginFromRepository() throws Exception {
        File remoteDir = Files.createTempDirectory("zerogit-remote").toFile();
        Git remoteGit = Git.init().setDirectory(remoteDir).call();
        try {
            Files.write(new File(remoteDir, "README.md").toPath(), "remote\n".getBytes(StandardCharsets.UTF_8));
            remoteGit.add().addFilepattern("README.md").call();
            remoteGit.commit().setMessage("remote init").call();
            remoteGit.tag().setName("release/a/1.4.0-202606041200").call();
            remoteGit.tag().setName("hotfix/a/1.4.1-202606041530").call();
            remoteGit.tag().setName("hotfix/a/1.4.1-202606041630").call();
        } finally {
            remoteGit.close();
        }

        File localDir = Files.createTempDirectory("zerogit-local").toFile();
        Git localGit = Git.init().setDirectory(localDir).call();
        try {
            Files.write(new File(localDir, "README.md").toPath(), "test\n".getBytes(StandardCharsets.UTF_8));
            localGit.add().addFilepattern("README.md").call();
            localGit.commit().setMessage("init").call();

            StoredConfig config = localGit.getRepository().getConfig();
            config.setString("remote", "origin", "url", remoteDir.getAbsolutePath());
            config.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*");
            config.save();
        } finally {
            localGit.close();
        }

        GitRepositoryService service = new GitRepositoryService();
        HotfixBaseTagInfo latest = service.getLatestRemoteHotfixBaseTag(localDir.getAbsolutePath());

        assertNotNull(latest);
        assertEquals("hotfix/a/1.4.1-202606041630", latest.getTagName());
    }
}
