package com.zerofinance.zerogit.eclipse.tests.exec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

import com.zerofinance.zerogit.eclipse.exec.ScriptResolver;

public class ScriptResolverTest {

    @Test
    public void prefersRepoRootScriptBeforeTempDownload() throws Exception {
        File repoRoot = Files.createTempDirectory("zerogit-repo").toFile();
        File localScript = new File(repoRoot, "StartNewFeature.sh");
        Files.write(localScript.toPath(), "#!/usr/bin/env bash\n".getBytes(StandardCharsets.UTF_8));

        ScriptResolver resolver = new ScriptResolver(new File(System.getProperty("java.io.tmpdir")));
        String resolved = resolver.resolveLocalFirst(repoRoot.getAbsolutePath(), "StartNewFeature.sh");

        assertEquals(localScript.getAbsolutePath().replace("\\", "/"), resolved);
    }

    @Test
    public void clearsKnownTempScripts() throws Exception {
        File tempDir = Files.createTempDirectory("zerogit-cache").toFile();
        File cached = new File(tempDir, "FinishRelease.sh");
        Files.write(cached.toPath(), "echo test".getBytes(StandardCharsets.UTF_8));

        ScriptResolver resolver = new ScriptResolver(tempDir);
        resolver.clearCache();

        assertTrue(!cached.exists());
    }
}
