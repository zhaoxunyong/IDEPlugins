package com.zerofinance.zerogit.eclipse.flow;

import java.util.Arrays;
import java.util.List;

public class SkillUpdateSupportTest {
    public static void main(String[] args) {
        parsesActionsAndBuildsFourArgumentGroups();
        rejectsInvalidProtocol();
    }

    private static void parsesActionsAndBuildsFourArgumentGroups() {
        List<SkillUpdateSupport.Skill> skills = SkillUpdateSupport.parse(
                "# mixed operations\n" +
                "update global git-commit code-review-expert git-commit\n" +
                "update project feature-shard-writer\n" +
                "delete global deprecated-skill\n" +
                "delete project old-java-style legacy-api\n");

        assertEquals(6, skills.size(), "skill count");
        assertEquals("Update · 全局 skill · git-commit", skills.get(0).toString(), "label");
        assertEquals("delete", skills.get(5).getAction(), "action");
        assertEquals(Arrays.asList(
                "--update-global", "git-commit", "code-review-expert",
                "--update-project", "feature-shard-writer",
                "--delete-global", "deprecated-skill",
                "--delete-project", "old-java-style", "legacy-api"),
                SkillUpdateSupport.buildArgs(skills), "args");
    }

    private static void rejectsInvalidProtocol() {
        for (final String output : Arrays.asList(
                "project: old-style",
                "{\"project\":[\"json-style\"]}",
                "install global unknown-action",
                "update local unknown-scope",
                "update global",
                "update project ../unsafe",
                "update global conflict\ndelete global conflict")) {
            assertIllegalArgument(new Runnable() {
                @Override
                public void run() {
                    SkillUpdateSupport.parse(output);
                }
            });
        }
    }

    private static void assertIllegalArgument(Runnable runnable) {
        try {
            runnable.run();
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + " expected <" + expected + "> but was <" + actual + ">");
        }
    }
}
