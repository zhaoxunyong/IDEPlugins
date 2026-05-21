package com.zerofinance.zerogitdeploy.tools;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public final class GitlabCiCommandReader {
    public static final String GITLAB_CI_FILE_NAME = ".gitlab-ci.yml";
    public static final String GITLAB_CI_NOT_FOUND_MESSAGE = "项目中未找到.gitlab-ci.yml文件";

    private GitlabCiCommandReader() {
    }

    public static List<GitlabCiCommandOption> readFromRepoRoot(String rootPath) throws IOException {
        File gitlabCiFile = new File(rootPath, GITLAB_CI_FILE_NAME);
        if (!gitlabCiFile.exists() || !gitlabCiFile.isFile()) {
            throw new IllegalArgumentException(GITLAB_CI_NOT_FOUND_MESSAGE);
        }
        String yamlText = Files.readString(gitlabCiFile.toPath(), StandardCharsets.UTF_8);
        return GitlabCiCommandParser.parse(yamlText);
    }
}
