package com.zerofinance.zerogitdeploy.tools;

import org.apache.commons.lang.StringUtils;

public class GitlabCiCommandOption {
    private final String sourceLabel;
    private final String command;

    public GitlabCiCommandOption(String sourceLabel, String command) {
        this.sourceLabel = sourceLabel;
        this.command = command;
    }

    public String getSourceLabel() {
        return sourceLabel;
    }

    public String getCommand() {
        return command;
    }

    public String getDisplayText() {
        if (StringUtils.isBlank(sourceLabel)) {
            return command;
        }
        return "[" + sourceLabel + "] " + command;
    }
}
