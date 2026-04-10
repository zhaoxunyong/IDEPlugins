package com.zerofinance.zerogitdeploy.setting;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author wengyongcheng
 * @since 2020/3/17 9:22 上午
 * application 级别的properties Component
 */
public class ZeroGitDeploySetting implements Configurable {
    private JTextField textField;
    private JPanel mainPanel;
    private JButton button1;
    private JCheckBox needDebugCheckBox;
    private JTextField scriptURLField;
    private JComboBox<String> groupNameComboBox;
    private JTextField groupNamesField;
    private JCheckBox checkGitVersionCheckBox;

    private static final String GIT_HOME_KEY = "gitDeployPluginGitHomeKey";

    private static final String SCRIPT_URL_KEY = "gitDeployPluginScriptURLKey";
    private static final String DEBUG_KEY = "gitDeployPluginDebugKey";
    private static final String GROUP_NAME_KEY = "gitDeployPluginGroupNameKey";
    private static final String GROUP_NAMES_KEY = "gitDeployPluginGroupNamesKey";
    private static final String CHECK_GIT_VERSION_KEY = "gitDeployPluginCheckGitVersionKey";
    private static final String DEFAULT_GROUP_NAMES = "a b c";

    public ZeroGitDeploySetting() {
        textField.setText(getGitHome());
        scriptURLField.setText(getScriptURL());
        needDebugCheckBox.setSelected(isDebug());
        String storedNames = PropertiesComponent.getInstance().getValue(GROUP_NAMES_KEY);
        groupNamesField.setText(StringUtils.isBlank(storedNames) ? DEFAULT_GROUP_NAMES : storedNames.trim());
        refreshGroupNameComboFromField();
        checkGitVersionCheckBox.setSelected(isCheckGitVersion());
        button1.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                FileChooserDescriptor chooserDescriptor = new FileChooserDescriptor(false,true,false,false,false,false);
                // 方式二，直接使用FileChooser.chooseFiles
                FileChooser.chooseFiles(chooserDescriptor, null, null, virtualFiles -> {
                    if (CollectionUtils.isNotEmpty(virtualFiles)) {
                        for (VirtualFile file : virtualFiles) {
                            //Messages.showMessageDialog(file.getPath(), file.getName(), Messages.getInformationIcon());
                            textField.setText(file.getPath());
                        }
                    }
                });
            }
        });
        groupNamesField.getDocument().addDocumentListener(new DocumentListener() {
            private void refresh() {
                SwingUtilities.invokeLater(() -> refreshGroupNameComboFromField());
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                refresh();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refresh();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refresh();
            }
        });
    }

    private void refreshGroupNameComboFromField() {
        Object previous = groupNameComboBox.getSelectedItem();
        List<String> tokens = getAllowedGroupTokensFromText(groupNamesField.getText());
        groupNameComboBox.removeAllItems();
        for (String g : tokens) {
            groupNameComboBox.addItem(g);
        }
        if (tokens.isEmpty()) {
            return;
        }
        String prevStr = previous == null ? "" : String.valueOf(previous);
        if (StringUtils.isNotBlank(prevStr) && tokens.contains(prevStr)) {
            groupNameComboBox.setSelectedItem(previous);
            return;
        }
        String savedGroup = PropertiesComponent.getInstance().getValue(GROUP_NAME_KEY);
        if (StringUtils.isNotBlank(savedGroup) && tokens.contains(savedGroup)) {
            groupNameComboBox.setSelectedItem(savedGroup);
        } else {
            groupNameComboBox.setSelectedItem(tokens.get(0));
        }
    }

    private static List<String> getAllowedGroupTokensFromText(String raw) {
        if (StringUtils.isBlank(raw)) {
            return Collections.emptyList();
        }
        return Arrays.asList(raw.trim().split("\\s+"));
    }

    private String persistedGroupNamesForCompare() {
        String stored = PropertiesComponent.getInstance().getValue(GROUP_NAMES_KEY);
        return StringUtils.isBlank(stored) ? DEFAULT_GROUP_NAMES : stored.trim();
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Git Deploy Settings";
    }


    @Nullable
    @Override
    public JComponent createComponent() {
        return mainPanel;
    }

    @Override
    public boolean isModified() {
        return !StringUtils.equals(textField.getText(),PropertiesComponent.getInstance().getValue(GIT_HOME_KEY))
                || !StringUtils.equals(String.valueOf(scriptURLField.getText()),PropertiesComponent.getInstance().getValue(SCRIPT_URL_KEY))
                || !StringUtils.equals(String.valueOf(needDebugCheckBox.isSelected()),PropertiesComponent.getInstance().getValue(DEBUG_KEY))
                || !StringUtils.equals(groupNamesField.getText().trim(), persistedGroupNamesForCompare())
                || !StringUtils.equals(String.valueOf(groupNameComboBox.getSelectedItem()), PropertiesComponent.getInstance().getValue(GROUP_NAME_KEY))
                || !StringUtils.equals(String.valueOf(checkGitVersionCheckBox.isSelected()), PropertiesComponent.getInstance().getValue(CHECK_GIT_VERSION_KEY));
    }

    @Override
    public void apply() throws ConfigurationException {
        List<String> tokens = new ArrayList<>(getAllowedGroupTokensFromText(groupNamesField.getText()));
        if (tokens.isEmpty()) {
            throw new ConfigurationException("Group names cannot be empty. Use space-separated values, e.g. a b c.");
        }
        Object selectedObj = groupNameComboBox.getSelectedItem();
        String selected = selectedObj == null ? "" : String.valueOf(selectedObj);
        if (StringUtils.isBlank(selected) || !tokens.contains(selected)) {
            throw new ConfigurationException("Group Default Name must be one of the names in Group Names.");
        }
        PropertiesComponent.getInstance().setValue(GIT_HOME_KEY, textField.getText());
        PropertiesComponent.getInstance().setValue(SCRIPT_URL_KEY, scriptURLField.getText());
        PropertiesComponent.getInstance().setValue(DEBUG_KEY, String.valueOf(needDebugCheckBox.isSelected()));
        PropertiesComponent.getInstance().setValue(GROUP_NAMES_KEY, groupNamesField.getText().trim());
        PropertiesComponent.getInstance().setValue(GROUP_NAME_KEY, String.valueOf(groupNameComboBox.getSelectedItem()));
        PropertiesComponent.getInstance().setValue(CHECK_GIT_VERSION_KEY, String.valueOf(checkGitVersionCheckBox.isSelected()));
    }

    public static String getGitHome() {
        return PropertiesComponent.getInstance().getValue(GIT_HOME_KEY);
    }

    public static String getScriptURL() {
        String scriptUrl = PropertiesComponent.getInstance().getValue(SCRIPT_URL_KEY);
        if(StringUtils.isBlank(scriptUrl)) {
            return "https://gitlab.zerofinance.net/dave.zhao/deployPlugin/-/raw/main/git-flow";
        }
        return scriptUrl;
    }

    public static boolean isDebug() {
        if(StringUtils.isBlank(PropertiesComponent.getInstance().getValue(DEBUG_KEY))) {
            return false;
        }
        return Boolean.valueOf(PropertiesComponent.getInstance().getValue(DEBUG_KEY));
    }

    /**
     * Space-separated group identifiers from settings (defaults to "a b c" if unset).
     */
    public static List<String> getAllowedGroupTokens() {
        String raw = PropertiesComponent.getInstance().getValue(GROUP_NAMES_KEY);
        if (StringUtils.isBlank(raw)) {
            return getAllowedGroupTokensFromText(DEFAULT_GROUP_NAMES);
        }
        return getAllowedGroupTokensFromText(raw);
    }

    public static String getGroupName() {
        List<String> allowed = getAllowedGroupTokens();
        if (allowed.isEmpty()) {
            return "";
        }
        String groupName = PropertiesComponent.getInstance().getValue(GROUP_NAME_KEY);
        if (StringUtils.isNotBlank(groupName) && allowed.contains(groupName)) {
            return groupName;
        }
        return allowed.get(0);
    }

    public static boolean isCheckGitVersion() {
        if (StringUtils.isBlank(PropertiesComponent.getInstance().getValue(CHECK_GIT_VERSION_KEY))) {
            return false;
        }
        return Boolean.parseBoolean(PropertiesComponent.getInstance().getValue(CHECK_GIT_VERSION_KEY));
    }
}
