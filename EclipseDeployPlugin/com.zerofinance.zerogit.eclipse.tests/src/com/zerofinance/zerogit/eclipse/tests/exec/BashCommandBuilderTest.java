package com.zerofinance.zerogit.eclipse.tests.exec;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

import com.zerofinance.zerogit.eclipse.exec.BashCommandBuilder;

public class BashCommandBuilderTest {

    @Test
    public void unixBatchCommandsUseBashAndRespectDebugFlag() {
        BashCommandBuilder builder = new BashCommandBuilder(false);
        String[] parts = builder.buildUnixBatch(true, "/tmp/StartNewFeature.sh", Arrays.asList("a", "feature/a/001-login"));

        assertEquals("bash", parts[0]);
        assertEquals("-x", parts[1]);
        assertEquals("/tmp/StartNewFeature.sh", parts[2]);
    }

    @Test
    public void windowsBatchCommandsAcceptGitInstallDirectory() {
        BashCommandBuilder builder = new BashCommandBuilder(true);
        String[] parts = builder.buildWindowsBatch("D:\\Developer\\Git", false, "/tmp/StartNewFeature.sh", Collections.<String>emptyList());

        assertEquals("D:\\Developer\\Git\\bin\\bash.exe", parts[0]);
        assertEquals("/tmp/StartNewFeature.sh", parts[1]);
    }

    @Test
    public void windowsBatchCommandsAcceptDirectBashExePath() {
        BashCommandBuilder builder = new BashCommandBuilder(true);
        String[] parts = builder.buildWindowsBatch("D:\\Developer\\Git\\bin\\bash.exe", true, "/tmp/StartNewFeature.sh", Collections.<String>emptyList());

        assertEquals("D:\\Developer\\Git\\bin\\bash.exe", parts[0]);
        assertEquals("-x", parts[1]);
        assertEquals("/tmp/StartNewFeature.sh", parts[2]);
    }

    @Test
    public void shellCommandsWrapRawCommandWithLoginShellExecution() {
        BashCommandBuilder builder = new BashCommandBuilder(true);
        String[] parts = builder.buildShellCommand(true, "D:\\Developer\\Git", "D:\\repo", "mvn -q test");

        assertEquals("D:\\Developer\\Git\\bin\\bash.exe", parts[0]);
        assertEquals("-x", parts[1]);
        assertEquals("-lc", parts[2]);
        assertEquals("cd 'D:/repo' && mvn -q test", parts[3]);
    }
}
