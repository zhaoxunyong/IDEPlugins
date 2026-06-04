package com.zerofinance.zerogit.eclipse.flow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang.StringUtils;

public class FinishReleaseOutputParser {
    private static final String READABLE_RELEASE_PREFIX = "Remaining release branches:";
    private static final String READABLE_HOTFIX_PREFIX = "Remaining hotfix branches:";
    private static final String MACHINE_PREFIX = "REMAINING_RELEASES:";

    public List<String> parseRemainingBranches(String output) {
        List<String> branches = new ArrayList<String>();
        for (String line : StringUtils.defaultString(output).split("\\R")) {
            String normalized = StringUtils.trimToEmpty(line);
            if (normalized.startsWith("+")) {
                continue;
            }
            if (normalized.startsWith(MACHINE_PREFIX)) {
                branches.addAll(splitSuffix(normalized, MACHINE_PREFIX));
                continue;
            }
            if (normalized.startsWith(READABLE_RELEASE_PREFIX)) {
                branches.addAll(splitSuffix(normalized, READABLE_RELEASE_PREFIX));
                continue;
            }
            if (normalized.startsWith(READABLE_HOTFIX_PREFIX)) {
                branches.addAll(splitSuffix(normalized, READABLE_HOTFIX_PREFIX));
            }
        }
        return branches;
    }

    private List<String> splitSuffix(String line, String prefix) {
        String suffix = StringUtils.trimToEmpty(StringUtils.substringAfter(line, prefix));
        if (StringUtils.isBlank(suffix) || "none".equalsIgnoreCase(suffix)) {
            return new ArrayList<String>();
        }
        return new ArrayList<String>(Arrays.asList(suffix.split("\\s+")));
    }
}
