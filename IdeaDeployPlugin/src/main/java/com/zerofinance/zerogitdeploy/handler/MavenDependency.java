package com.zerofinance.zerogitdeploy.handler;

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

    private Map<String, String> dependencies;

    public File getPomFile() {
        return pomFile;
    }

    public void setPomFile(File pomFile) {
        this.pomFile = pomFile;
    }

    public Map<String, String> getDependencies() {
        return dependencies;
    }

    public void setDependencies(Map<String, String> dependencies) {
        this.dependencies = dependencies;
    }
}
