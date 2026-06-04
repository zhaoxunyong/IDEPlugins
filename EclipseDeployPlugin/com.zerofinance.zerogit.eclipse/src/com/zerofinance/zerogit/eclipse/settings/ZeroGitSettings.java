package com.zerofinance.zerogit.eclipse.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.preferences.ScopedPreferenceStore;

import com.zerofinance.zerogit.eclipse.plugin.ZeroGitPlugin;

public final class ZeroGitSettings {
    public static final String DEFAULT_SCRIPT_URL =
            "https://gitlab.zerofinance.net/dave.zhao/deployPlugin/-/raw/main/git-flow";
    public static final String DEFAULT_GROUP_NAMES = "a b c";
    public static final String DEFAULT_ASSIGNEES = "faker.zhou justin.wang conan.chen rain.he";

    private ZeroGitSettings() {
    }

    public static List<String> parseGroups(String raw) {
        return parseSpaceSeparated(raw);
    }

    public static List<String> parseAssignees(String raw) {
        return parseSpaceSeparated(raw);
    }

    public static String resolveDefaultGroup(List<String> groups, String configuredDefault) {
        String normalizedDefault = StringUtils.trimToEmpty(configuredDefault);
        if (groups.isEmpty()) {
            return "";
        }
        if (StringUtils.isNotBlank(normalizedDefault) && groups.contains(normalizedDefault)) {
            return normalizedDefault;
        }
        return groups.get(0);
    }

    public static boolean parseBoolean(String raw) {
        return Boolean.parseBoolean(StringUtils.defaultString(raw));
    }

    public static IPreferenceStore store() {
        return new ScopedPreferenceStore(InstanceScope.INSTANCE, ZeroGitPlugin.PLUGIN_ID);
    }

    public static String getGitHome() {
        return StringUtils.trimToEmpty(store().getString(PreferenceConstants.GIT_HOME));
    }

    public static String getScriptUrl() {
        String configured = StringUtils.trimToEmpty(store().getString(PreferenceConstants.SCRIPT_URL));
        return StringUtils.isBlank(configured) ? DEFAULT_SCRIPT_URL : configured;
    }

    public static boolean isDebugEnabled() {
        return store().getBoolean(PreferenceConstants.DEBUG);
    }

    public static List<String> getGroups() {
        List<String> groups = parseGroups(store().getString(PreferenceConstants.GROUP_NAMES));
        return groups.isEmpty() ? parseGroups(DEFAULT_GROUP_NAMES) : groups;
    }

    public static String getDefaultGroup() {
        return resolveDefaultGroup(getGroups(), store().getString(PreferenceConstants.DEFAULT_GROUP));
    }

    public static List<String> getGitMrAssignees() {
        List<String> assignees = parseAssignees(store().getString(PreferenceConstants.GIT_MR_ASSIGNEES));
        return assignees.isEmpty() ? parseAssignees(DEFAULT_ASSIGNEES) : assignees;
    }

    public static boolean isGitVersionCheckEnabled() {
        return store().getBoolean(PreferenceConstants.CHECK_GIT_VERSION);
    }

    private static List<String> parseSpaceSeparated(String raw) {
        String normalized = StringUtils.trimToEmpty(raw);
        if (StringUtils.isBlank(normalized)) {
            return Collections.emptyList();
        }
        String[] tokens = normalized.split("\\s+");
        List<String> values = new ArrayList<String>();
        for (String token : tokens) {
            if (StringUtils.isNotBlank(token)) {
                values.add(token);
            }
        }
        return Collections.unmodifiableList(values);
    }
}
