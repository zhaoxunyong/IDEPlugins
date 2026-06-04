package com.zerofinance.zerogit.eclipse.tests.ci;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

import com.zerofinance.zerogit.eclipse.ci.GitlabCiCommandOption;
import com.zerofinance.zerogit.eclipse.ci.GitlabCiCommandParser;

public class GitlabCiCommandParserTest {

    @Test
    public void extractsBaseExecCommandsFromRootAndJobVariables() {
        String yaml =
                "variables:\n" +
                "  BASE_EXEC_CMD: ./gradlew test\n" +
                "build:\n" +
                "  variables:\n" +
                "    BASE_EXEC_CMD: mvn -q test\n";

        List<GitlabCiCommandOption> options = GitlabCiCommandParser.parse(yaml);

        assertEquals(2, options.size());
        assertEquals("./gradlew test", options.get(0).getCommand());
        assertEquals("mvn -q test", options.get(1).getCommand());
    }

    @Test
    public void splitsSemicolonSeparatedCommandsAndDeduplicatesThem() {
        String yaml =
                "BASE_EXEC_CMD: mvn -q test; ./gradlew test; mvn -q test\n" +
                "lint:\n" +
                "  variables:\n" +
                "    BASE_EXEC_CMD: ./gradlew lint\n";

        List<GitlabCiCommandOption> options = GitlabCiCommandParser.parse(yaml);

        assertEquals(3, options.size());
        assertEquals("mvn -q test", options.get(0).getCommand());
        assertEquals("./gradlew test", options.get(1).getCommand());
        assertEquals("./gradlew lint", options.get(2).getCommand());
    }
}
