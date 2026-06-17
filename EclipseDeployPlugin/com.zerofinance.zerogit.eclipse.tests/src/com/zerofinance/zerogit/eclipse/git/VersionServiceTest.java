package com.zerofinance.zerogit.eclipse.git;

import java.util.Arrays;

public class VersionServiceTest {
    public static void main(String[] args) {
        selectsFirstDatedRemoteTagFromCreatorDateSortedRefs();
    }

    private static void selectsFirstDatedRemoteTagFromCreatorDateSortedRefs() {
        VersionService.HotfixBaseTagInfo latest = new VersionService().findLatestHotfixBaseTag(Arrays.asList(
                "release/a/1.2.0-202405011200|2024-05-01T12:00:00+08:00",
                "v9.9.9|2026-01-01T00:00:00+08:00",
                "hotfix/b/1.1.9-202404301000|2024-04-30T10:00:00+08:00"));

        assertEquals("release/a/1.2.0-202405011200", latest.getTagName(), "tagName");
        assertEquals("1.2.0", latest.getVersion(), "version");
        assertEquals("202405011200", latest.getTimestamp(), "timestamp");
    }

    private static void assertEquals(String expected, String actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + " expected <" + expected + "> but was <" + actual + ">");
        }
    }
}
