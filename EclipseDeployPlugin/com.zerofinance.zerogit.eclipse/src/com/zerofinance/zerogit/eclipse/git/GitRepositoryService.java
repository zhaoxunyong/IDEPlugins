package com.zerofinance.zerogit.eclipse.git;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.LsRemoteCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

public class GitRepositoryService {
    private final VersionService versionService;

    public GitRepositoryService() {
        this(new VersionService());
    }

    public GitRepositoryService(VersionService versionService) {
        this.versionService = versionService;
    }

    public String findRepositoryRoot(String path) throws IOException {
        File start = new File(StringUtils.trimToEmpty(path));
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        builder.findGitDir(start.isFile() ? start.getParentFile() : start);
        File gitDir = builder.getGitDir();
        if (gitDir == null) {
            throw new IllegalArgumentException("Making sure this is a git project!");
        }
        return normalizePath(gitDir.getParentFile());
    }

    public String readCurrentBranch(String repoRoot) throws IOException {
        Repository repository = openRepository(repoRoot);
        try {
            String fullBranch = repository.getFullBranch();
            if (StringUtils.isBlank(fullBranch)) {
                return "";
            }
            if (fullBranch.startsWith("refs/")) {
                return Repository.shortenRefName(fullBranch);
            }
            return fullBranch;
        } finally {
            repository.close();
        }
    }

    public List<String> listLocalFeatureBranches(String repoRoot, String group) throws IOException, GitAPIException {
        return listBranches(repoRoot, "feature/" + StringUtils.trimToEmpty(group) + "/", true, false);
    }

    public List<String> listReleaseBranches(String repoRoot, String group, boolean includeLocal)
            throws IOException, GitAPIException {
        return versionService.sortBranchesBySemverDesc(
                listBranches(repoRoot, "release/" + StringUtils.trimToEmpty(group) + "/", includeLocal, true));
    }

    public List<String> listAllReleaseBranches(String repoRoot, boolean includeLocal) throws IOException, GitAPIException {
        return versionService.sortBranchesBySemverDesc(listBranches(repoRoot, "release/", includeLocal, true));
    }

    public List<String> listHotfixBranches(String repoRoot, String group, boolean includeLocal)
            throws IOException, GitAPIException {
        return versionService.sortBranchesBySemverDesc(
                listBranches(repoRoot, "hotfix/" + StringUtils.trimToEmpty(group) + "/", includeLocal, true));
    }

    public List<String> listAllHotfixBranches(String repoRoot, boolean includeLocal) throws IOException, GitAPIException {
        return versionService.sortBranchesBySemverDesc(listBranches(repoRoot, "hotfix/", includeLocal, true));
    }

    public VersionService.HotfixBaseTagInfo getLatestRemoteHotfixBaseTag(String repoRoot)
            throws IOException, GitAPIException {
        List<String> tagNames = new ArrayList<String>();
        Git git = Git.open(new File(repoRoot));
        try {
            LsRemoteCommand command = git.lsRemote();
            command.setRemote("origin");
            command.setTags(true);
            command.setHeads(false);

            for (Ref ref : command.call()) {
                tagNames.add(StringUtils.removeStart(ref.getName(), "refs/tags/"));
            }
        } finally {
            git.close();
        }
        return versionService.findLatestHotfixBaseTag(tagNames);
    }

    public boolean hasStagedChanges(String repoRoot) throws IOException, GitAPIException {
        Git git = Git.open(new File(repoRoot));
        try {
            Status status = git.status().call();
            return !status.getAdded().isEmpty()
                    || !status.getChanged().isEmpty()
                    || !status.getRemoved().isEmpty();
        } finally {
            git.close();
        }
    }

    private List<String> listBranches(String repoRoot, String branchPrefix, boolean includeLocal, boolean includeRemote)
            throws IOException, GitAPIException {
        Set<String> branches = new LinkedHashSet<String>();
        Git git = Git.open(new File(repoRoot));
        try {
            if (includeLocal) {
                for (Ref ref : git.branchList().call()) {
                    addBranchIfMatches(branches, ref, branchPrefix, false);
                }
            }
            if (includeRemote) {
                for (Ref ref : git.branchList().setListMode(ListMode.REMOTE).call()) {
                    addBranchIfMatches(branches, ref, branchPrefix, true);
                }
            }
            return new ArrayList<String>(branches);
        } finally {
            git.close();
        }
    }

    private void addBranchIfMatches(Set<String> branches, Ref ref, String branchPrefix, boolean remote) {
        String shortName = Repository.shortenRefName(ref.getName());
        String normalized = remote ? StringUtils.removeStart(shortName, "origin/") : shortName;
        if (normalized.startsWith(branchPrefix)) {
            branches.add(normalized);
        }
    }

    private Repository openRepository(String repoRoot) throws IOException {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        return builder.setWorkTree(new File(repoRoot)).readEnvironment().findGitDir(new File(repoRoot)).build();
    }

    private String normalizePath(File directory) {
        return directory.getAbsolutePath().replace("\\", "/");
    }
}
