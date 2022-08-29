package com.zerofinance.zerogitdeploy.setting;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.zerofinance.zerogitdeploy.tools.DeployCmdExecuter;
import com.zerofinance.zerogitdeploy.tools.MessagesUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

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
    private JCheckBox moreDetailsCheckBox;
    private JTextField scriptURLField;
    private JCheckBox runningInTerminalCheckBox;

    private JCheckBox skipRepoChangeConfirmCheckBox;

    private static final String GIT_HOME_KEY = "gitDeployPluginGitHomeKey";

    private static final String SCRIPT_URL_KEY = "gitDeployPluginScriptURLKey";
    private static final String DEBUG_KEY = "gitDeployPluginDebugKey";
    private static final String MORE_DETAILS_KEY = "gitDeployPluginMoreDetailsKey";

    private static final String RUNNING_IN_TERMINAL_KEY = "gitDeployPluginRunningInTerminalKey";

    private static final String SKIP_REPO_CHANGE_CONFIRM_KEY = "skipRepoChangeConfirmKey";

    public ZeroGitDeploySetting() {
        textField.setText(PropertiesComponent.getInstance().getValue(GIT_HOME_KEY));
        String scriptURL = PropertiesComponent.getInstance().getValue(SCRIPT_URL_KEY);
        if(StringUtils.isNotBlank(scriptURL)) {
            scriptURLField.setText(scriptURL);
        } else {
            scriptURLField.setText("http://gitlab.zerofinance.net/dave.zhao/deployPlugin/raw/master");
        }
        needDebugCheckBox.setSelected(isDebug());
        moreDetailsCheckBox.setSelected(isMoreDetails());
        runningInTerminalCheckBox.setSelected(isRunnInTerminal());
        skipRepoChangeConfirmCheckBox.setSelected(isSkipRepoChangeConfirmKey());
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
        runningInTerminalCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if(runningInTerminalCheckBox.isSelected()) {
                    Messages.showInfoMessage("Making sure \"Settings->Tools->Terminal->Shell Path\" is configured as a full path of bash.exe.", "Information");
                }
            }
        });
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
                || !StringUtils.equals(String.valueOf(moreDetailsCheckBox.isSelected()),PropertiesComponent.getInstance().getValue(MORE_DETAILS_KEY))
                || !StringUtils.equals(String.valueOf(runningInTerminalCheckBox.isSelected()),PropertiesComponent.getInstance().getValue(RUNNING_IN_TERMINAL_KEY))
                || !StringUtils.equals(String.valueOf(skipRepoChangeConfirmCheckBox.isSelected()),PropertiesComponent.getInstance().getValue(SKIP_REPO_CHANGE_CONFIRM_KEY));
    }

    @Override
    public void apply() throws ConfigurationException {
        PropertiesComponent.getInstance().setValue(GIT_HOME_KEY, textField.getText());
        PropertiesComponent.getInstance().setValue(SCRIPT_URL_KEY, scriptURLField.getText());
        PropertiesComponent.getInstance().setValue(DEBUG_KEY, String.valueOf(needDebugCheckBox.isSelected()));
        PropertiesComponent.getInstance().setValue(MORE_DETAILS_KEY, String.valueOf(moreDetailsCheckBox.isSelected()));
        PropertiesComponent.getInstance().setValue(RUNNING_IN_TERMINAL_KEY, String.valueOf(runningInTerminalCheckBox.isSelected()));
        PropertiesComponent.getInstance().setValue(SKIP_REPO_CHANGE_CONFIRM_KEY, String.valueOf(skipRepoChangeConfirmCheckBox.isSelected()));
    }

    public static String getGitHome() {
        return PropertiesComponent.getInstance().getValue(GIT_HOME_KEY);
    }

    public static String getScriptURL() {
        return PropertiesComponent.getInstance().getValue(SCRIPT_URL_KEY);
    }

    public static boolean isDebug() {
        if(StringUtils.isBlank(PropertiesComponent.getInstance().getValue(DEBUG_KEY))) {
            return false;
        }
        return Boolean.valueOf(PropertiesComponent.getInstance().getValue(DEBUG_KEY));
    }

    public static boolean isMoreDetails() {
        if(StringUtils.isBlank(PropertiesComponent.getInstance().getValue(MORE_DETAILS_KEY))) {
            return false;
        }
        return Boolean.valueOf(PropertiesComponent.getInstance().getValue(MORE_DETAILS_KEY));
    }

    public static boolean isRunnInTerminal() {
        if(StringUtils.isBlank(PropertiesComponent.getInstance().getValue(RUNNING_IN_TERMINAL_KEY))) {
            return false;
        }
        return Boolean.valueOf(PropertiesComponent.getInstance().getValue(RUNNING_IN_TERMINAL_KEY));
    }

    public static boolean isSkipRepoChangeConfirmKey() {
        if(StringUtils.isBlank(PropertiesComponent.getInstance().getValue(SKIP_REPO_CHANGE_CONFIRM_KEY))) {
            return false;
        }
        return Boolean.valueOf(PropertiesComponent.getInstance().getValue(SKIP_REPO_CHANGE_CONFIRM_KEY));
    }
}
