package com.zerofinance.zerogit.eclipse.git;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GitVersionSupport {
    private static final Pattern GIT_VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)");

    private GitVersionSupport() {
    }

    public static void assertSupportedVersion(String gitVersionOutput) {
        Matcher matcher = GIT_VERSION_PATTERN.matcher(gitVersionOutput);
        if (!matcher.find()) {
            throw new IllegalArgumentException("无法识别 Git 版本，请确认 Git 已安装。");
        }
        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        if (major < 2 || (major == 2 && minor < 29)) {
            throw new IllegalArgumentException("Git 版本需要 >= 2.29，请先升级。");
        }
    }
}
