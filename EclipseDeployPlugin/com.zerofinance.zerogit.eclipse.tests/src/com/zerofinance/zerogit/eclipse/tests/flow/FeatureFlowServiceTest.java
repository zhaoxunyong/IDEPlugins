package com.zerofinance.zerogit.eclipse.tests.flow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;

import org.junit.Test;

import com.zerofinance.zerogit.eclipse.flow.ZeroGitFlowService;

public class FeatureFlowServiceTest {

    @Test
    public void startNewFeatureBuildsExpectedScriptArguments() {
        ZeroGitFlowService service = new ZeroGitFlowService();

        assertEquals(
                Arrays.asList("a", "feature/a/001-login"),
                service.buildStartNewFeatureArgs("a", "feature/a/001-login"));
    }

    @Test
    public void mergeRequestBuildsExpectedScriptArguments() {
        ZeroGitFlowService service = new ZeroGitFlowService();

        assertEquals(
                Arrays.asList("a", "faker.zhou"),
                service.buildMergeRequestArgs("a", "faker.zhou"));
    }

    @Test
    public void finishFeatureSortsNumericPrefixesDescending() {
        ZeroGitFlowService service = new ZeroGitFlowService();

        assertEquals(
                Arrays.asList("feature/a/010-login", "feature/a/002-api", "feature/a/001-ui"),
                service.sortFeatureBranches(Arrays.asList("feature/a/001-ui", "feature/a/010-login", "feature/a/002-api")));
    }

    @Test
    public void validFeatureBranchNamePassesValidation() {
        ZeroGitFlowService service = new ZeroGitFlowService();

        assertNull(service.validateFeatureBranchName("a", "feature/a/001-login"));
    }

    @Test
    public void invalidFeatureBranchNameReturnsReadableMessage() {
        ZeroGitFlowService service = new ZeroGitFlowService();

        assertEquals(
                "Feature name must start with number- (e.g. 001-login).",
                service.validateFeatureBranchName("a", "feature/a/login"));
    }
}
