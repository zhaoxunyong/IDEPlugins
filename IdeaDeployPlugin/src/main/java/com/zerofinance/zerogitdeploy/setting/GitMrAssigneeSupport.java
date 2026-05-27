package com.zerofinance.zerogitdeploy.setting;

import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

public final class GitMrAssigneeSupport {
    public static final String DEFAULT_GIT_MR_ASSIGNEES = "faker.zhou justin.wang conan.chen rain.he";
    public static final String ASSIGNEE_PLACEHOLDER = "请选择 assignee，或手动填写其他 GitLab 用户名";

    private GitMrAssigneeSupport() {
    }

    public static List<String> getConfiguredGitMrAssignees(String raw) {
        String effective = StringUtils.isBlank(raw) ? DEFAULT_GIT_MR_ASSIGNEES : raw.trim();
        if (StringUtils.isBlank(effective)) {
            return new ArrayList<>();
        }
        return new ArrayList<>(new LinkedHashSet<>(Arrays.asList(effective.split("\\s+"))));
    }

    public static List<String> buildGitMrAssigneeChooserValues(List<String> assignees) {
        List<String> values = new ArrayList<>();
        values.add(ASSIGNEE_PLACEHOLDER);
        values.addAll(new LinkedHashSet<>(assignees));
        return values;
    }

    public static String normalizeGitMrAssigneeSelection(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String normalized = raw.trim();
        if (ASSIGNEE_PLACEHOLDER.equals(normalized)) {
            return null;
        }
        return normalized;
    }

    public static String getMissingGitMrAssigneeMessage() {
        return "请选择 assignee，或手动填写其他 assignee 后再发起 Merge Request。";
    }
}
