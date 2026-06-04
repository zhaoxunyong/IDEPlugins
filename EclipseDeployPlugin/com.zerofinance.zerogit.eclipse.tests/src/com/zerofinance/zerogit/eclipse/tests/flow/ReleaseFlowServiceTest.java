package com.zerofinance.zerogit.eclipse.tests.flow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;

import org.junit.Test;

import com.zerofinance.zerogit.eclipse.flow.FinishReleaseOutputParser;
import com.zerofinance.zerogit.eclipse.flow.ZeroGitFlowService;

public class ReleaseFlowServiceTest {

    @Test
    public void finishReleaseParsesRemainingBranchesFromReadableOutput() {
        FinishReleaseOutputParser parser = new FinishReleaseOutputParser();

        assertEquals(
                Arrays.asList("release/a/1.5.0", "hotfix/a/1.4.2"),
                parser.parseRemainingBranches(
                        "Remaining release branches: release/a/1.5.0\n"
                                + "Remaining hotfix branches: hotfix/a/1.4.2"));
    }

    @Test
    public void finishReleaseParsesMachineReadableRemainingBranchesAndIgnoresTraceLines() {
        FinishReleaseOutputParser parser = new FinishReleaseOutputParser();

        assertEquals(
                Arrays.asList("release/a/1.5.0", "hotfix/a/1.4.2"),
                parser.parseRemainingBranches(
                        "+ git fetch origin --prune\n"
                                + "REMAINING_RELEASES: release/a/1.5.0 hotfix/a/1.4.2"));
    }

    @Test
    public void startReleaseUsesGroupAndSelectedBranchArguments() {
        ZeroGitFlowService service = new ZeroGitFlowService();

        assertEquals(
                Arrays.asList("a", "release/a/1.5.0"),
                service.buildStartReleaseArgs("a", "release/a/1.5.0"));
    }

    @Test
    public void finishReleaseUsesSingleSelectedBranchArgument() {
        ZeroGitFlowService service = new ZeroGitFlowService();

        assertEquals(
                Arrays.asList("release/a/1.5.0"),
                service.buildFinishReleaseArgs("release/a/1.5.0"));
    }

    @Test
    public void startHotfixUsesGroupBranchAndBaseTagArguments() {
        ZeroGitFlowService service = new ZeroGitFlowService();

        assertEquals(
                Arrays.asList("a", "hotfix/a/1.4.2", "v1.4.1"),
                service.buildStartHotfixArgs("a", "hotfix/a/1.4.2", "v1.4.1"));
    }

    @Test
    public void finishHotfixUsesSingleSelectedBranchArgument() {
        ZeroGitFlowService service = new ZeroGitFlowService();

        assertEquals(
                Arrays.asList("hotfix/a/1.4.2"),
                service.buildFinishHotfixArgs("hotfix/a/1.4.2"));
    }

    @Test
    public void suggestsNextReleaseMavenVersionFromSnapshotOrRc() {
        ZeroGitFlowService service = new ZeroGitFlowService();

        assertEquals("1.2.3-RC1", service.suggestMavenVersion("1.2.3-SNAPSHOT", "release"));
        assertEquals("1.2.3-RC3", service.suggestMavenVersion("1.2.3-RC2", "release"));
    }

    @Test
    public void suggestsNextSnapshotMavenVersionByIncrementingPatch() {
        ZeroGitFlowService service = new ZeroGitFlowService();

        assertEquals("1.2.4-SNAPSHOT", service.suggestMavenVersion("1.2.3-RC2", "snapshot"));
        assertEquals("1.2.4-SNAPSHOT", service.suggestMavenVersion("1.2.3", "snapshot"));
    }

    @Test
    public void rejectsInvalidMavenVersionFormatsOrMismatchedTypes() {
        ZeroGitFlowService service = new ZeroGitFlowService();

        assertEquals(
                "Maven version must be x.y.z, x.y.z-SNAPSHOT or x.y.z-RCN (N为数字).",
                service.validateMavenVersion("foo", "release"));
        assertEquals(
                "Release 版本不能以 -SNAPSHOT 结尾。",
                service.validateMavenVersion("1.2.3-SNAPSHOT", "release"));
        assertEquals(
                "Snapshot 版本必须以 -SNAPSHOT 结尾。",
                service.validateMavenVersion("1.2.3-RC1", "snapshot"));
        assertNull(service.validateMavenVersion("1.2.3-RC1", "release"));
        assertNull(service.validateMavenVersion("1.2.4-SNAPSHOT", "snapshot"));
    }
}
