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
}
