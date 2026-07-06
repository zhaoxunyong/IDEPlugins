package com.zerofinance.zerogitdeploy.handler;

import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class HotfixTagSelector {
    private static final Pattern DATED_RELEASE_OR_HOTFIX_TAG_PATTERN =
            Pattern.compile("^(release|hotfix)/[^/]+/(\\d+\\.\\d+\\.\\d+)-(\\d{12})$");

    private HotfixTagSelector() {
    }

    static @Nullable HotfixBaseTagInfo pickLatestDatedRemoteTag(List<String> sortedTagRefs) {
        if (sortedTagRefs == null) {
            return null;
        }
        for (String line : sortedTagRefs) {
            String tagName = StringUtils.substringBefore(StringUtils.defaultString(line), "|").trim();
            if (StringUtils.isBlank(tagName)) {
                continue;
            }
            Matcher matcher = DATED_RELEASE_OR_HOTFIX_TAG_PATTERN.matcher(tagName);
            if (!matcher.matches()) {
                continue;
            }
            return new HotfixBaseTagInfo(tagName, matcher.group(2), matcher.group(3));
        }
        return null;
    }

    static final class HotfixBaseTagInfo {
        private final String tagName;
        private final String version;
        private final String timestamp;

        HotfixBaseTagInfo(String tagName, String version, String timestamp) {
            this.tagName = tagName;
            this.version = version;
            this.timestamp = timestamp;
        }

        String getTagName() {
            return tagName;
        }

        String getVersion() {
            return version;
        }

        String getTimestamp() {
            return timestamp;
        }
    }
}
