package com.zerofinance.zerogit.eclipse.git;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

public class VersionService {
    private static final Pattern SEMVER_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)$");
    private static final Pattern SEMVER_IN_TEXT_PATTERN = Pattern.compile("(\\d+\\.\\d+\\.\\d+)");
    private static final Pattern SEMVER_ONLY_TAG_PATTERN = Pattern.compile("^v?(\\d+\\.\\d+\\.\\d+)(\\^\\{\\})?$");
    private static final Pattern REMOTE_TAG_PATTERN =
            Pattern.compile("^(release|hotfix)/[^/]+/(\\d+\\.\\d+\\.\\d+)-(\\d{12})(\\^\\{\\})?$");

    public String suggestNextRelease(List<String> tagsAndBranches, List<String> sameGroupBranches, String group) {
        String maxVersion = maxSemverVersion(tagsAndBranches);
        String candidateVersion = nextMinor(maxVersion);
        List<String> sameGroupVersions = extractVersions(sameGroupBranches);
        while (sameGroupVersions.contains(candidateVersion)) {
            candidateVersion = nextMinor(candidateVersion);
        }
        return "release/" + StringUtils.trimToEmpty(group) + "/" + candidateVersion;
    }

    public String suggestNextHotfix(String baseTag, List<String> sameGroupBranches, String group) {
        List<String> versions = new ArrayList<String>();
        String baseVersion = extractVersion(baseTag);
        if (isSemver(baseVersion)) {
            versions.add(baseVersion);
        }
        versions.addAll(extractVersions(sameGroupBranches));

        String candidateVersion = nextPatch(maxSemverVersion(versions));
        List<String> sameGroupVersions = extractVersions(sameGroupBranches);
        while (sameGroupVersions.contains(candidateVersion)) {
            candidateVersion = nextPatch(candidateVersion);
        }
        return "hotfix/" + StringUtils.trimToEmpty(group) + "/" + candidateVersion;
    }

    public HotfixBaseTagInfo findLatestHotfixBaseTag(List<String> tagNames) {
        HotfixBaseTagInfo latest = null;
        for (String tagName : tagNames == null ? Collections.<String>emptyList() : tagNames) {
            HotfixBaseTagInfo current = parseHotfixBaseTag(tagName);
            if (current == null) {
                continue;
            }
            if (latest == null
                    || compareSemver(current.getVersion(), latest.getVersion()) > 0
                    || (compareSemver(current.getVersion(), latest.getVersion()) == 0
                            && current.getTimestamp().compareTo(latest.getTimestamp()) > 0)) {
                latest = current;
            }
        }
        return latest;
    }

    public List<String> sortBranchesBySemverDesc(List<String> branches) {
        List<String> copy = new ArrayList<String>(branches == null ? Collections.<String>emptyList() : branches);
        Collections.sort(copy, (left, right) -> compareSemver(extractVersion(right), extractVersion(left)));
        return copy;
    }

    public List<String> extractVersions(List<String> values) {
        List<String> versions = new ArrayList<String>();
        for (String value : values == null ? Collections.<String>emptyList() : values) {
            String version = extractVersion(value);
            if (isSemver(version)) {
                versions.add(version);
            }
        }
        return versions;
    }

    public String extractVersion(String value) {
        String normalized = StringUtils.trimToEmpty(value);
        Matcher semverOnlyTagMatcher = SEMVER_ONLY_TAG_PATTERN.matcher(normalized);
        if (semverOnlyTagMatcher.matches()) {
            return semverOnlyTagMatcher.group(1);
        }

        Matcher remoteTagMatcher = REMOTE_TAG_PATTERN.matcher(normalized);
        if (remoteTagMatcher.matches()) {
            return remoteTagMatcher.group(2);
        }

        if (normalized.indexOf('/') >= 0) {
            String suffix = StringUtils.substringAfterLast(normalized, "/");
            if (isSemver(suffix)) {
                return suffix;
            }
        }

        Matcher semverMatcher = SEMVER_IN_TEXT_PATTERN.matcher(normalized);
        if (semverMatcher.find()) {
            return semverMatcher.group(1);
        }
        return "";
    }

    public int compareSemver(String left, String right) {
        if (!isSemver(left) || !isSemver(right)) {
            return StringUtils.defaultString(left).compareTo(StringUtils.defaultString(right));
        }
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        for (int i = 0; i < 3; i++) {
            int diff = Integer.parseInt(leftParts[i]) - Integer.parseInt(rightParts[i]);
            if (diff != 0) {
                return diff;
            }
        }
        return 0;
    }

    public String nextMinor(String semver) {
        Matcher matcher = SEMVER_PATTERN.matcher(StringUtils.defaultString(semver));
        if (!matcher.matches()) {
            return "1.0.0";
        }
        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        return major + "." + (minor + 1) + ".0";
    }

    public String nextPatch(String semver) {
        Matcher matcher = SEMVER_PATTERN.matcher(StringUtils.defaultString(semver));
        if (!matcher.matches()) {
            return "1.0.0";
        }
        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        int patch = Integer.parseInt(matcher.group(3));
        return major + "." + minor + "." + (patch + 1);
    }

    private String maxSemverVersion(List<String> values) {
        List<String> versions = extractVersions(values);
        if (versions.isEmpty()) {
            return "1.0.0";
        }
        Collections.sort(versions, this::compareSemver);
        return versions.get(versions.size() - 1);
    }

    private boolean isSemver(String value) {
        return SEMVER_PATTERN.matcher(StringUtils.defaultString(value)).matches();
    }

    private HotfixBaseTagInfo parseHotfixBaseTag(String tagName) {
        String normalized = StringUtils.trimToEmpty(tagName);

        Matcher semverOnlyTagMatcher = SEMVER_ONLY_TAG_PATTERN.matcher(normalized);
        if (semverOnlyTagMatcher.matches()) {
            return new HotfixBaseTagInfo(normalized, semverOnlyTagMatcher.group(1), "");
        }

        Matcher remoteTagMatcher = REMOTE_TAG_PATTERN.matcher(normalized);
        if (remoteTagMatcher.matches()) {
            return new HotfixBaseTagInfo(normalized, remoteTagMatcher.group(2), remoteTagMatcher.group(3));
        }
        return null;
    }

    public static final class HotfixBaseTagInfo {
        private final String tagName;
        private final String version;
        private final String timestamp;

        public HotfixBaseTagInfo(String tagName, String version, String timestamp) {
            this.tagName = tagName;
            this.version = version;
            this.timestamp = timestamp;
        }

        public String getTagName() {
            return tagName;
        }

        public String getVersion() {
            return version;
        }

        public String getTimestamp() {
            return timestamp;
        }
    }
}
