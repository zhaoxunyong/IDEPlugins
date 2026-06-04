package com.zerofinance.zerogit.eclipse.flow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

public class ZeroGitFlowService {
    public static final String GITFLOW_GUIDELINE_URL =
            "https://v04jaasnl45.feishu.cn/wiki/Vg5PwK2smiPxGLk7w4Gc7tZanjb";

    private static final Pattern FEATURE_SUFFIX_PATTERN = Pattern.compile("^(\\d+)-\\S.*$");
    private static final Pattern SEMVER_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)$");
    private static final Pattern MAVEN_VERSION_PATTERN =
            Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(-SNAPSHOT|-RC\\d+)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RC_SUFFIX_PATTERN = Pattern.compile("-RC(\\d+)$", Pattern.CASE_INSENSITIVE);

    public List<String> buildStartNewFeatureArgs(String group, String branchName) {
        return Arrays.asList(StringUtils.trimToEmpty(group), StringUtils.trimToEmpty(branchName));
    }

    public List<String> buildFinishFeatureArgs(String group, String branchName) {
        return Arrays.asList(StringUtils.trimToEmpty(group), StringUtils.trimToEmpty(branchName));
    }

    public List<String> buildRebaseFeatureArgs(String group, String branchName) {
        return Arrays.asList(StringUtils.trimToEmpty(group), StringUtils.trimToEmpty(branchName));
    }

    public List<String> buildMergeRequestArgs(String group, String assignee) {
        return Arrays.asList(StringUtils.trimToEmpty(group), StringUtils.trimToEmpty(assignee));
    }

    public List<String> buildMavenChangeArgs(String group, String mavenVersion) {
        return Arrays.asList(StringUtils.trimToEmpty(group), StringUtils.trimToEmpty(mavenVersion));
    }

    public List<String> buildStartReleaseArgs(String group, String branchName) {
        return Arrays.asList(StringUtils.trimToEmpty(group), StringUtils.trimToEmpty(branchName));
    }

    public List<String> buildFinishReleaseArgs(String branchName) {
        return Arrays.asList(StringUtils.trimToEmpty(branchName));
    }

    public List<String> buildStartHotfixArgs(String group, String branchName, String baseTag) {
        return Arrays.asList(
                StringUtils.trimToEmpty(group),
                StringUtils.trimToEmpty(branchName),
                StringUtils.trimToEmpty(baseTag));
    }

    public List<String> buildFinishHotfixArgs(String branchName) {
        return Arrays.asList(StringUtils.trimToEmpty(branchName));
    }

    public List<String> sortFeatureBranches(List<String> branches) {
        List<String> sorted = new ArrayList<String>(branches == null ? Collections.<String>emptyList() : branches);
        Collections.sort(sorted, new Comparator<String>() {
            @Override
            public int compare(String left, String right) {
                int leftPrefix = extractFeaturePrefixNumber(left);
                int rightPrefix = extractFeaturePrefixNumber(right);
                if (leftPrefix != rightPrefix) {
                    return rightPrefix - leftPrefix;
                }
                return StringUtils.defaultString(right).compareTo(StringUtils.defaultString(left));
            }
        });
        return sorted;
    }

    public String validateFeatureBranchName(String group, String fullBranchName) {
        String normalizedGroup = StringUtils.trimToEmpty(group);
        String normalizedBranch = StringUtils.trimToEmpty(fullBranchName);
        String prefix = "feature/" + normalizedGroup + "/";
        if (!normalizedBranch.startsWith(prefix)) {
            return "Branch name must start with \"" + prefix + "\".";
        }
        String suffix = normalizedBranch.substring(prefix.length()).trim();
        if (StringUtils.isBlank(suffix)) {
            return "Please input feature name after the prefix.";
        }
        if (!FEATURE_SUFFIX_PATTERN.matcher(suffix).matches()) {
            return "Feature name must start with number- (e.g. 001-login).";
        }
        return null;
    }

    public String validateReleaseBranchName(String group, String fullBranchName) {
        return validateVersionBranchName("release", group, fullBranchName, "Release");
    }

    public String validateHotfixBranchName(String group, String fullBranchName) {
        return validateVersionBranchName("hotfix", group, fullBranchName, "Hotfix");
    }

    public String suggestMavenVersion(String currentVersion, String changeType) {
        String raw = StringUtils.trimToEmpty(currentVersion);
        String normalizedType = StringUtils.trimToEmpty(changeType).toLowerCase();
        if (StringUtils.isBlank(raw)) {
            return "release".equals(normalizedType) ? null : "1.0.1-SNAPSHOT";
        }
        if ("snapshot".equals(normalizedType)) {
            String baseVersion = raw.contains("-")
                    ? StringUtils.substringBefore(raw, "-")
                    : raw.replaceFirst("(?i)-SNAPSHOT$", "");
            return nextPatch(baseVersion) + "-SNAPSHOT";
        }

        Matcher rcMatcher = RC_SUFFIX_PATTERN.matcher(raw);
        if (rcMatcher.find()) {
            int n = Integer.parseInt(rcMatcher.group(1));
            return raw.substring(0, rcMatcher.start()) + "-RC" + (n + 1);
        }
        if (StringUtils.endsWithIgnoreCase(raw, "-SNAPSHOT")) {
            return raw.replaceFirst("(?i)-SNAPSHOT$", "") + "-RC1";
        }
        return null;
    }

    public String validateMavenVersion(String mavenVersion, String changeType) {
        String normalizedVersion = StringUtils.trimToEmpty(mavenVersion);
        String normalizedType = StringUtils.trimToEmpty(changeType).toLowerCase();
        if (!MAVEN_VERSION_PATTERN.matcher(normalizedVersion).matches()) {
            return "Maven version must be x.y.z, x.y.z-SNAPSHOT or x.y.z-RCN (N为数字).";
        }
        if ("release".equals(normalizedType) && StringUtils.endsWithIgnoreCase(normalizedVersion, "-SNAPSHOT")) {
            return "Release 版本不能以 -SNAPSHOT 结尾。";
        }
        if ("snapshot".equals(normalizedType) && !StringUtils.endsWithIgnoreCase(normalizedVersion, "-SNAPSHOT")) {
            return "Snapshot 版本必须以 -SNAPSHOT 结尾。";
        }
        return null;
    }

    public String validateCurrentFeatureBranch(String group, String currentBranch) {
        String normalizedGroup = StringUtils.trimToEmpty(group);
        String normalizedBranch = StringUtils.trimToEmpty(currentBranch);
        String prefix = "feature/" + normalizedGroup + "/";
        if (StringUtils.isBlank(normalizedBranch) || !normalizedBranch.startsWith(prefix)) {
            return "当前分支 \"" + (StringUtils.isBlank(normalizedBranch) ? "(无)" : normalizedBranch)
                    + "\" 不是 feature 分支（应以 " + prefix + " 开头），操作已取消。";
        }
        return null;
    }

    public String normalizeAssignee(String rawAssignee) {
        String assignee = StringUtils.trimToEmpty(rawAssignee);
        return StringUtils.isBlank(assignee) ? null : assignee;
    }

    private int extractFeaturePrefixNumber(String branchName) {
        String branch = StringUtils.defaultString(branchName);
        String suffix = branch.substring(branch.lastIndexOf('/') + 1);
        Matcher matcher = FEATURE_SUFFIX_PATTERN.matcher(suffix);
        if (!matcher.matches()) {
            return -1;
        }
        return Integer.parseInt(matcher.group(1));
    }

    private String validateVersionBranchName(String kind, String group, String fullBranchName, String label) {
        String normalizedGroup = StringUtils.trimToEmpty(group);
        String normalizedBranch = StringUtils.trimToEmpty(fullBranchName);
        String prefix = kind + "/" + normalizedGroup + "/";
        if (!normalizedBranch.startsWith(prefix)) {
            return label + " branch must start with \"" + prefix + "\".";
        }
        String version = StringUtils.trimToEmpty(normalizedBranch.substring(prefix.length()));
        if (!SEMVER_PATTERN.matcher(version).matches()) {
            return label + " version must follow SemVer format, e.g. 1.0.0.";
        }
        return null;
    }

    private String nextPatch(String semver) {
        Matcher matcher = SEMVER_PATTERN.matcher(StringUtils.defaultString(semver));
        if (!matcher.matches()) {
            return "1.0.1";
        }
        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        int patch = Integer.parseInt(matcher.group(3));
        return major + "." + minor + "." + (patch + 1);
    }
}
