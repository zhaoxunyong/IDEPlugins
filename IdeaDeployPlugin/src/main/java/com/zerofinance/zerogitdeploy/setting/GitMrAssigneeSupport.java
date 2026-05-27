package com.zerofinance.zerogitdeploy.setting;

import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

public final class GitMrAssigneeSupport {
    public static final String DEFAULT_GIT_MR_ASSIGNEES = "faker.zhou justin.wang conan.chen rain.he";

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
        return new ArrayList<>(new LinkedHashSet<>(assignees));
    }

    public static String normalizeGitMrAssigneeSelection(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        return raw.trim();
    }

    public static String getMissingGitMrAssigneeMessage() {
        return "请选择 assignee，或手动填写其他 assignee 后再发起 Merge Request。";
    }
}
