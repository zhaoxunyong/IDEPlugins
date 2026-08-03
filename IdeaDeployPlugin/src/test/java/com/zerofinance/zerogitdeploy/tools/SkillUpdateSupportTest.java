package com.zerofinance.zerogitdeploy.tools;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class SkillUpdateSupportTest {
    @Test
    public void parsesActionsAndBuildsFourArgumentGroups() {
        List<SkillUpdateSupport.Skill> skills = SkillUpdateSupport.parse(
                "# mixed operations\n" +
                "update global git-commit code-review-expert git-commit\n" +
                "update project feature-shard-writer\n" +
                "delete global deprecated-skill\n" +
                "delete project old-java-style legacy-api\n");

        assertEquals(6, skills.size());
        assertEquals("Update · 全局 skill · git-commit", skills.get(0).toString());
        assertEquals("delete", skills.get(5).getAction());
        assertEquals(Arrays.asList(
                "--update-global", "git-commit", "code-review-expert",
                "--update-project", "feature-shard-writer",
                "--delete-global", "deprecated-skill",
                "--delete-project", "old-java-style", "legacy-api"),
                SkillUpdateSupport.buildArgs(skills));
    }

    @Test
    public void rejectsInvalidProtocol() {
        for (String output : Arrays.asList(
                "project: old-style",
                "{\"project\":[\"json-style\"]}",
                "install global unknown-action",
                "update local unknown-scope",
                "update global",
                "update project ../unsafe",
                "update global conflict\ndelete global conflict")) {
            assertIllegalArgument(output);
        }
    }

    private static void assertIllegalArgument(String output) {
        try {
            SkillUpdateSupport.parse(output);
            fail("应拒绝非法协议: " + output);
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
