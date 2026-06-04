package com.zerofinance.zerogit.eclipse.settings;

import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.DirectoryFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

public class ZeroGitPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {
    private StringFieldEditor groupNamesEditor;
    private StringFieldEditor defaultGroupEditor;

    public ZeroGitPreferencePage() {
        super(GRID);
        setPreferenceStore(ZeroGitSettings.store());
        setDescription("ZeroGit Eclipse plugin settings.");
    }

    @Override
    public void createFieldEditors() {
        DirectoryFieldEditor gitHomeEditor =
                new DirectoryFieldEditor(
                        PreferenceConstants.GIT_HOME,
                        "&Git Home Directory (Windows only):",
                        getFieldEditorParent());
        gitHomeEditor.setEmptyStringAllowed(true);
        addField(gitHomeEditor);

        addField(new StringFieldEditor(
                PreferenceConstants.SCRIPT_URL,
                "Script URL:",
                getFieldEditorParent()));

        addField(new BooleanFieldEditor(
                PreferenceConstants.DEBUG,
                "Run bash with debug output (-x)",
                getFieldEditorParent()));

        groupNamesEditor = new StringFieldEditor(
                PreferenceConstants.GROUP_NAMES,
                "Group Names (space separated):",
                getFieldEditorParent());
        groupNamesEditor.setEmptyStringAllowed(true);
        addField(groupNamesEditor);

        defaultGroupEditor = new StringFieldEditor(
                PreferenceConstants.DEFAULT_GROUP,
                "Default Group:",
                getFieldEditorParent());
        defaultGroupEditor.setEmptyStringAllowed(true);
        addField(defaultGroupEditor);

        addField(new StringFieldEditor(
                PreferenceConstants.GIT_MR_ASSIGNEES,
                "Git MR Assignees (space separated):",
                getFieldEditorParent()));

        addField(new BooleanFieldEditor(
                PreferenceConstants.CHECK_GIT_VERSION,
                "Require Git version >= 2.29",
                getFieldEditorParent()));
    }

    @Override
    protected void checkState() {
        super.checkState();
        if (!isValid()) {
            return;
        }

        List<String> groups = ZeroGitSettings.parseGroups(groupNamesEditor.getStringValue());
        if (groups.isEmpty()) {
            groups = ZeroGitSettings.parseGroups(ZeroGitSettings.DEFAULT_GROUP_NAMES);
        }
        String configuredDefault = StringUtils.trimToEmpty(defaultGroupEditor.getStringValue());

        if (StringUtils.isNotBlank(configuredDefault) && !groups.contains(configuredDefault)) {
            setErrorMessage("Default Group must be one of the configured Group Names.");
            setValid(false);
            return;
        }

        setErrorMessage(null);
        setValid(true);
    }

    @Override
    public boolean performOk() {
        groupNamesEditor.setStringValue(StringUtils.trimToEmpty(groupNamesEditor.getStringValue()));
        String configuredDefault = StringUtils.trimToEmpty(defaultGroupEditor.getStringValue());
        defaultGroupEditor.setStringValue(configuredDefault);
        if (StringUtils.isBlank(configuredDefault)) {
            List<String> groups = ZeroGitSettings.parseGroups(groupNamesEditor.getStringValue());
            if (groups.isEmpty()) {
                groups = ZeroGitSettings.parseGroups(ZeroGitSettings.DEFAULT_GROUP_NAMES);
            }
            defaultGroupEditor.setStringValue(ZeroGitSettings.resolveDefaultGroup(groups, ""));
        }
        return super.performOk();
    }

    @Override
    public void init(IWorkbench workbench) {
    }
}
