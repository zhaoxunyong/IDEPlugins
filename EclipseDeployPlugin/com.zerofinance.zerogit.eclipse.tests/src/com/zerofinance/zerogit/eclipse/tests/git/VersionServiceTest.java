package com.zerofinance.zerogit.eclipse.tests.git;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

import com.zerofinance.zerogit.eclipse.git.VersionService;

public class VersionServiceTest {

    @Test
    public void suggestsNextReleaseMinorFromGlobalMaxVersion() {
        VersionService service = new VersionService();
        String suggestion = service.suggestNextRelease(
                Arrays.asList("v1.2.3", "release/a/1.3.0-202606041530", "hotfix/a/1.2.4-202606041600"),
                Arrays.asList("release/a/1.4.0"),
                "a");

        assertEquals("release/a/1.5.0", suggestion);
    }

    @Test
    public void suggestsNextHotfixPatchFromProductionBase() {
        VersionService service = new VersionService();
        String suggestion = service.suggestNextHotfix(
                "v1.4.0",
                Arrays.asList("hotfix/a/1.4.1"),
                "a");

        assertEquals("hotfix/a/1.4.2", suggestion);
    }
}
