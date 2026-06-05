package com.zerofinance.zerogit.eclipse.git;

import java.io.IOException;

public class GitVersionChecker {
    private final GitRepositoryService gitRepositoryService;

    public GitVersionChecker() {
        this(new GitRepositoryService());
    }

    public GitVersionChecker(GitRepositoryService gitRepositoryService) {
        this.gitRepositoryService = gitRepositoryService;
    }

    public void ensureSupportedVersion(String repoRoot) throws IOException {
        GitVersionSupport.assertSupportedVersion(gitRepositoryService.readGitVersion(repoRoot));
    }

    public void assertSupportedVersion(String gitVersionOutput) {
        GitVersionSupport.assertSupportedVersion(gitVersionOutput);
    }
}
