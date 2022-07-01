package com.aeasycredit.deployplugin.preferences;

import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

import com.aeasycredit.deployplugin.DeployPluginLauncherPlugin;

/**
 * Class used to initialize default preference values.
 */
public class PreferenceInitializer extends AbstractPreferenceInitializer {

    private ResourceBundle resourceBundle;

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer#initializeDefaultPreferences()
	 */
	public void initializeDefaultPreferences() {
		IPreferenceStore store = DeployPluginLauncherPlugin.getDefault().getPreferenceStore();
		store.setDefault(PreferenceConstants.GIT_SCRIPTS_URL, getGitUrl());
		store.setDefault(PreferenceConstants.GIT_SHOWTAG_IN_DROPDOWN, false);
		store.setDefault(PreferenceConstants.GIT_RELEASE_WITH_TAG, false);
		store.setDefault(PreferenceConstants.GIT_BASH_DEBUG, false);
//		store.setDefault(PreferenceConstants.CODE_GEN_URL, getCodeGenUrl());
//		store.setDefault(PreferenceConstants.P_CHOICE, "choice2");
//		store.setDefault(PreferenceConstants.P_STRING, "Defaults value");
	}

	  private String getGitUrl() {
	  	String msg = getResourceString("git.url");
	     return msg;
	  }

//	  private String getCodeGenUrl() {
//	  	String msg = getResourceString("code.gen.url");
//	     return msg;
//	  }

    /**
     * Returns the string from the plugin's resource bundle,
     * or 'key' if not found.
     */
  	private String getResourceString(String key) {
        ResourceBundle bundle= getResourceBundle();
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
    }

    /**
     * Returns the plugin's resource bundle,
     */
  	private ResourceBundle getResourceBundle() {
        try {
            resourceBundle= PropertyResourceBundle.getBundle("resources");
        } catch (MissingResourceException x) {
            resourceBundle = null;
        }
        return resourceBundle;
    }

}
