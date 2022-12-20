package com.zerofinance.zerogitdeploy.handler;

import javax.swing.*;
import java.io.File;
import java.util.Map;

/**
 * <p>
 * <a href="MavenDependency.java"><i>View Source</i></a>
 *
 * @author Dave.zhao
 * Date: 12/17/2022 3:16 PM
 */
public class MavenDependency {

    private File pomFile;

    private String moduleName;

    private String currVersion;

    private String latestVersion;

    private javax.swing.JTextField textField;

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    public File getPomFile() {
        return pomFile;
    }

    public void setPomFile(File pomFile) {
        this.pomFile = pomFile;
    }

    public String getCurrVersion() {
        return currVersion;
    }

    public void setCurrVersion(String currVersion) {
        this.currVersion = currVersion;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public void setLatestVersion(String latestVersion) {
        this.latestVersion = latestVersion;
    }

    public JTextField getTextField() {
        return textField;
    }

    public void setTextField(JTextField textField) {
        this.textField = textField;
    }
}
