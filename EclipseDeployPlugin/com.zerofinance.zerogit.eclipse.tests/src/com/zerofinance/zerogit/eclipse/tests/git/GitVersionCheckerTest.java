package com.zerofinance.zerogit.eclipse.tests.git;

import org.junit.Test;

import com.zerofinance.zerogit.eclipse.git.GitVersionSupport;

public class GitVersionCheckerTest {

    @Test
    public void acceptsGitVersionTwoTwentyNineAndAbove() throws Exception {
        GitVersionSupport.assertSupportedVersion("git version 2.29.0");
        GitVersionSupport.assertSupportedVersion("git version 2.39.5");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsGitVersionBelowTwoTwentyNine() {
        GitVersionSupport.assertSupportedVersion("git version 2.28.0");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnparseableGitVersionOutput() {
        GitVersionSupport.assertSupportedVersion("version unknown");
    }
}
