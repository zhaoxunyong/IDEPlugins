package com.aeasycredit.deployplugin.preferences;

import org.eclipse.jface.preference.DirectoryFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import com.aeasycredit.deployplugin.DeployPluginLauncherPlugin;

/**
 * This class represents a preference page that is contributed to the
 * Preferences dialog. By subclassing <samp>FieldEditorPreferencePage</samp>, we
 * can use the field support built into JFace that allows us to create a page
 * that is small and knows how to save, restore and apply itself.
 * <p>
 * This page is used to modify preferences only. They are stored in the
 * preference store that belongs to the main plug-in class. That way,
 * preferences can be accessed directly via the preference store.
 */

public class DeployPluginPerencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

	public DeployPluginPerencePage() {
		super(GRID);
		setPreferenceStore(DeployPluginLauncherPlugin.getDefault().getPreferenceStore());
		setDescription("A preference page for deploy plugin");
	}

	/**
	 * Creates the field editors. Field editors are abstractions of the common
	 * GUI blocks needed to manipulate various types of preferences. Each field
	 * editor knows how to save and restore itself.
	 */
	public void createFieldEditors() {
		addField(new DirectoryFieldEditor(PreferenceConstants.GIT_HOME_PATH, "&Git Home Directory(Only for windows):", getFieldEditorParent()));
		
		addField(new StringFieldEditor(PreferenceConstants.GIT_SCRIPTS_URL, "Gitlab url for download scripts:", getFieldEditorParent()));
		
		addField(new StringFieldEditor(PreferenceConstants.GIT_USER_URL, "Gitlab username(optional):", getFieldEditorParent()));
		StringFieldEditor password = new StringFieldEditor(PreferenceConstants.GIT_PWD_URL, "Gitlab password(optional):", getFieldEditorParent());
		password.getTextControl(getFieldEditorParent()).setEchoChar('*');
		
		addField(password);
		
		addField(new BooleanFieldEditor(PreferenceConstants.GIT_BASH_DEBUG, "Bash in debug model?", getFieldEditorParent()));
		
		addField(new BooleanFieldEditor(PreferenceConstants.GIT_SHOWTAG_IN_DROPDOWN, "Show tag in dropdown?", getFieldEditorParent()));
		addField(new BooleanFieldEditor(PreferenceConstants.GIT_RELEASE_WITH_TAG, "Tag the release version automatically?", getFieldEditorParent()));

//		addField(new StringFieldEditor(PreferenceConstants.CODE_GEN_URL, "Code Gen Url:", getFieldEditorParent()));
//		addField(new DirectoryFieldEditor(PreferenceConstants.CODE_GEN_JS_PATH, "&The Js Path Of Generating Code:", getFieldEditorParent()));
		
//		addField(new RadioGroupFieldEditor(PreferenceConstants.P_CHOICE, "An example of a multiple-choice preference", 1, new String[][] { { "&Choice 1", "choice1" }, { "C&hoice 2", "choice2" } }, getFieldEditorParent()));
//		addField(new StringFieldEditor(PreferenceConstants.P_STRING, "A &text preference:", getFieldEditorParent()));
//		addField(new ComboFieldEditor("id", "New ComboFieldEditor", new String[][]{{"name_1", "value_1"}, {"name_2", "value_2"}}, getFieldEditorParent()));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.ui.IWorkbenchPreferencePage#init(org.eclipse.ui.IWorkbench)
	 */
	public void init(IWorkbench workbench) {
	}

}