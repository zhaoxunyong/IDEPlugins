package com.zerofinance.zerogit.eclipse.exec;

import java.io.File;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;

public class BashCommandBuilder {
    private final boolean windows;

    public BashCommandBuilder(boolean windows) {
        this.windows = windows;
    }

    public String[] buildUnixBatch(boolean debug, String scriptPath, List<String> args) {
        List<String> safeArgs = args == null ? Collections.<String>emptyList() : args;
        int offset = debug ? 3 : 2;
        String[] parts = new String[offset + safeArgs.size()];
        parts[0] = "bash";
        int index = 1;
        if (debug) {
            parts[index++] = "-x";
        }
        parts[index++] = scriptPath;
        for (String arg : safeArgs) {
            parts[index++] = arg;
        }
        return parts;
    }

    public String[] buildWindowsBatch(String gitHome, boolean debug, String scriptPath, List<String> args) {
        List<String> safeArgs = args == null ? Collections.<String>emptyList() : args;
        String bashPath = resolveWindowsBashPath(gitHome);
        int offset = debug ? 3 : 2;
        String[] parts = new String[offset + safeArgs.size()];
        parts[0] = bashPath;
        int index = 1;
        if (debug) {
            parts[index++] = "-x";
        }
        parts[index++] = scriptPath;
        for (String arg : safeArgs) {
            parts[index++] = arg;
        }
        return parts;
    }

    public String[] build(boolean debug, String gitHome, String scriptPath, List<String> args) {
        boolean runOnWindows = windows || SystemUtils.IS_OS_WINDOWS;
        if (runOnWindows && StringUtils.isBlank(gitHome)) {
            throw new IllegalArgumentException("Git home is required when building Windows bash commands.");
        }
        return runOnWindows
                ? buildWindowsBatch(gitHome, debug, scriptPath, args)
                : buildUnixBatch(debug, scriptPath, args);
    }

    private String resolveWindowsBashPath(String gitHome) {
        String normalized = StringUtils.trimToEmpty(gitHome).replace("/", File.separator);
        if (StringUtils.endsWithIgnoreCase(normalized, File.separator + "bash.exe")
                || StringUtils.endsWithIgnoreCase(normalized, "bash.exe")) {
            return normalized;
        }
        return normalized + File.separator + "bin" + File.separator + "bash.exe";
    }
}
