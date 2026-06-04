package com.zerofinance.zerogit.eclipse.exec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CommandRequest {
    private final String repoRoot;
    private final String scriptName;
    private final List<String> args;
    private final boolean debug;
    private final String gitHome;

    public CommandRequest(String repoRoot, String scriptName, List<String> args, boolean debug, String gitHome) {
        this.repoRoot = repoRoot;
        this.scriptName = scriptName;
        this.args = Collections.unmodifiableList(new ArrayList<String>(args == null ? Collections.<String>emptyList() : args));
        this.debug = debug;
        this.gitHome = gitHome;
    }

    public String getRepoRoot() {
        return repoRoot;
    }

    public String getScriptName() {
        return scriptName;
    }

    public List<String> getArgs() {
        return args;
    }

    public boolean isDebug() {
        return debug;
    }

    public String getGitHome() {
        return gitHome;
    }
}
