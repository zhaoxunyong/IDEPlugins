package com.zerofinance.zerogit.eclipse.settings;

import java.util.List;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

public class ZeroGitPreferenceInitializer extends AbstractPreferenceInitializer {
    @Override
    public void initializeDefaultPreferences() {
        IPreferenceStore store = ZeroGitSettings.store();
        List<String> defaultGroups = ZeroGitSettings.parseGroups(ZeroGitSettings.DEFAULT_GROUP_NAMES);

        store.setDefault(PreferenceConstants.GIT_HOME, "");
        store.setDefault(PreferenceConstants.SCRIPT_URL, ZeroGitSettings.DEFAULT_SCRIPT_URL);
        store.setDefault(PreferenceConstants.DEBUG, false);
        store.setDefault(PreferenceConstants.GROUP_NAMES, ZeroGitSettings.DEFAULT_GROUP_NAMES);
        store.setDefault(PreferenceConstants.DEFAULT_GROUP, ZeroGitSettings.resolveDefaultGroup(defaultGroups, ""));
        store.setDefault(PreferenceConstants.GIT_MR_ASSIGNEES, ZeroGitSettings.DEFAULT_ASSIGNEES);
        store.setDefault(PreferenceConstants.CHECK_GIT_VERSION, false);
    }
}
