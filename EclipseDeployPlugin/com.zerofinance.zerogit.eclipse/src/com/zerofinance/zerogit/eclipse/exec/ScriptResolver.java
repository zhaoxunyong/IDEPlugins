package com.zerofinance.zerogit.eclipse.exec;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

public class ScriptResolver {
    private static final List<String> KNOWN_SCRIPTS = Arrays.asList(
            "gitCheck.sh",
            "GenCommitMessage.sh",
            "AiCodeReview.sh",
            "GetSkills.sh",
            "UpdateSkills.sh",
            "MavenChange.sh",
            "StartNewFeature.sh",
            "FinishFeature.sh",
            "RebaseFeature.sh",
            "GitMergeRequest.sh",
            "StartNewRelease.sh",
            "FinishRelease.sh",
            "StartNewHotfix.sh");

    private final File tempDirectory;

    public ScriptResolver(File tempDirectory) {
        this.tempDirectory = tempDirectory;
    }

    public void clearCache() {
        for (String script : KNOWN_SCRIPTS) {
            File candidate = new File(tempDirectory, script);
            FileUtils.deleteQuietly(candidate);
        }
    }

    public String resolveLocalFirst(String repoRoot, String scriptName) {
        File localScript = new File(repoRoot, normalizeScriptName(scriptName));
        if (localScript.exists()) {
            return normalizePath(localScript);
        }
        return normalizePath(new File(tempDirectory, normalizeScriptName(scriptName)));
    }

    public String resolve(String repoRoot, String scriptName, String scriptBaseUrl) throws IOException {
        File localScript = new File(repoRoot, normalizeScriptName(scriptName));
        if (localScript.exists()) {
            return normalizePath(localScript);
        }

        if (StringUtils.isBlank(scriptBaseUrl)) {
            throw new IllegalArgumentException("Script base URL must not be blank.");
        }

        if (!tempDirectory.exists()) {
            FileUtils.forceMkdir(tempDirectory);
        }
        File target = new File(tempDirectory, normalizeScriptName(scriptName));
        FileUtils.copyURLToFile(new URL(buildRemoteUrl(scriptBaseUrl, scriptName)), target);
        return normalizePath(target);
    }

    private String buildRemoteUrl(String scriptBaseUrl, String scriptName) {
        String base = StringUtils.removeEnd(StringUtils.trim(scriptBaseUrl), "/");
        return base + "/" + normalizeScriptName(scriptName);
    }

    private String normalizeScriptName(String scriptName) {
        return StringUtils.removeStart(StringUtils.defaultString(scriptName), "./");
    }

    private String normalizePath(File file) {
        return file.getAbsolutePath().replace("\\", "/");
    }
}
