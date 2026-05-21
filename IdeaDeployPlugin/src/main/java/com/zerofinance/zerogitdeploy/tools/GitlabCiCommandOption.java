package com.zerofinance.zerogitdeploy.tools;

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
        return command;
    }
}
