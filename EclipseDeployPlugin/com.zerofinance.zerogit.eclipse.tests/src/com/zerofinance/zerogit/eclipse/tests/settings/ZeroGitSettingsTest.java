package com.zerofinance.zerogit.eclipse.tests.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

import com.zerofinance.zerogit.eclipse.settings.ZeroGitSettings;

public class ZeroGitSettingsTest {

    @Test
    public void parsesSpaceSeparatedGroupsAndFallsBackToFirstGroup() {
        assertEquals(Arrays.asList("a", "b", "c"), ZeroGitSettings.parseGroups("a b c"));
        assertEquals("a", ZeroGitSettings.resolveDefaultGroup(Arrays.asList("a", "b", "c"), ""));
    }

    @Test
    public void keepsConfiguredDefaultGroupWhenPresent() {
        assertEquals("b", ZeroGitSettings.resolveDefaultGroup(Arrays.asList("a", "b", "c"), "b"));
        assertEquals("b", ZeroGitSettings.resolveDefaultGroup(Arrays.asList("a", "b", "c"), " b "));
    }

    @Test
    public void debugAndGitVersionChecksDefaultToFalse() {
        assertFalse(ZeroGitSettings.parseBoolean(""));
        assertFalse(ZeroGitSettings.parseBoolean(null));
    }

    @Test
    public void blankParsingStaysEmptyUntilEffectiveGettersApplyDefaults() {
        assertEquals(Collections.emptyList(), ZeroGitSettings.parseGroups("   "));
        assertEquals(Collections.emptyList(), ZeroGitSettings.parseAssignees(null));
    }
}
