package com.aeasycredit.deployplugin;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import com.aeasycredit.deployplugin.preferences.PreferenceConstants;

/**
 * The activator class controls the plug-in life cycle
 */
public class DeployPluginLauncherPlugin extends AbstractUIPlugin {

	// The plug-in ID
	public static final String PLUGIN_ID = "com.aeasycredit.deployplugin"; //$NON-NLS-1$

//    private ResourceBundle resourceBundle;
    
	// The shared instance
	private static DeployPluginLauncherPlugin plugin;
	
	/**
	 * The constructor
	 */
	public DeployPluginLauncherPlugin() {
        super();
        plugin = this;
//        try {
//            resourceBundle= PropertyResourceBundle.getBundle("resources");
//        } catch (MissingResourceException x) {
//            resourceBundle = null;
//        }
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception {
		plugin = null;
		super.stop(context);
	}

	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static DeployPluginLauncherPlugin getDefault() {
		return plugin;
	}

	/**
	 * Returns an image descriptor for the image file at the given
	 * plug-in relative path
	 *
	 * @param path the path
	 * @return the image descriptor
	 */
	public static ImageDescriptor getImageDescriptor(String path) {
		return imageDescriptorFromPlugin(PLUGIN_ID, path);
	}

    /**
     * Returns the workspace instance.
     */
    public static IWorkspace getWorkspace() {
        return ResourcesPlugin.getWorkspace();
    }

    /**
     * Returns the active shell for this plugin.
     */
    public static Shell getShell() {
        return getDefault().getWorkbench().getActiveWorkbenchWindow().getShell();
    }

//    static public String getMsgStart() {
//    	String msg = getResourceString("msg.start");
//    	MessageDialog.openWarning(getShell(),"Tomcat", msg);
//       return msg;
//    }

    /**
     * Returns the string from the plugin's resource bundle,
     * or 'key' if not found.
    public static String getResourceString(String key) {
        ResourceBundle bundle= getDefault().getResourceBundle();
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
    }
     */

    /**
     * Returns the plugin's resource bundle,
    public ResourceBundle getResourceBundle() {
        try {
            resourceBundle= PropertyResourceBundle.getBundle("resources");
        } catch (MissingResourceException x) {
            resourceBundle = null;
        }
        return resourceBundle;
    }
     */

    static public void log(String msg) {
        ILog log = DeployPluginLauncherPlugin.getDefault().getLog();
        Status status = new Status(IStatus.ERROR, PLUGIN_ID, IStatus.ERROR, msg + "\n", null);
        log.log(status);
    }

    static public void log(Exception ex) {
        ILog log = DeployPluginLauncherPlugin.getDefault().getLog();
        StringWriter stringWriter = new StringWriter();
        ex.printStackTrace(new PrintWriter(stringWriter));
        String msg = stringWriter.getBuffer().toString();

        Status status = new Status(IStatus.ERROR, PLUGIN_ID, IStatus.ERROR, msg, null);
        log.log(status);
    }

    public static String getGitHomePath() {
        IPreferenceStore pref =	DeployPluginLauncherPlugin.getDefault().getPreferenceStore();
        return pref.getString(PreferenceConstants.GIT_HOME_PATH);
    }
    
    public static String getGitScriptsUrl() {
        IPreferenceStore pref =	DeployPluginLauncherPlugin.getDefault().getPreferenceStore();
        return pref.getString(PreferenceConstants.GIT_SCRIPTS_URL);
    }
    
//    public static String getCodeGenUrl() {
//        IPreferenceStore pref =	DeployPluginLauncherPlugin.getDefault().getPreferenceStore();
//        return pref.getString(PreferenceConstants.CODE_GEN_URL);
//    }
//
//    public static String getCodeGenJsPath() {
//        IPreferenceStore pref =	DeployPluginLauncherPlugin.getDefault().getPreferenceStore();
//        return pref.getString(PreferenceConstants.CODE_GEN_JS_PATH);
//    }

    public static String getGitUsername() {
        IPreferenceStore pref =	DeployPluginLauncherPlugin.getDefault().getPreferenceStore();
        return pref.getString(PreferenceConstants.GIT_USER_URL);
    }
    
    public static String getGitPassword() {
        IPreferenceStore pref =	DeployPluginLauncherPlugin.getDefault().getPreferenceStore();
        return pref.getString(PreferenceConstants.GIT_PWD_URL);
    }
    
    public static boolean getGitShowTagInDropDown() {
        IPreferenceStore pref =	DeployPluginLauncherPlugin.getDefault().getPreferenceStore();
        return pref.getBoolean(PreferenceConstants.GIT_SHOWTAG_IN_DROPDOWN);
    }
    
    public static boolean getGitReleaseWithTag() {
        IPreferenceStore pref =	DeployPluginLauncherPlugin.getDefault().getPreferenceStore();
        return pref.getBoolean(PreferenceConstants.GIT_RELEASE_WITH_TAG);
    }
    
    public static boolean getGitBashDebug() {
        IPreferenceStore pref =	DeployPluginLauncherPlugin.getDefault().getPreferenceStore();
        return pref.getBoolean(PreferenceConstants.GIT_BASH_DEBUG);
    }
}
