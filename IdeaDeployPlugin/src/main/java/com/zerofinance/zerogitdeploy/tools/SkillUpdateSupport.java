package com.zerofinance.zerogitdeploy.tools;

import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 解析 GetSkills.sh 输出并构造 UpdateSkills.sh 参数。 */
public final class SkillUpdateSupport {
    private SkillUpdateSupport() {
    }

    public static final class Skill {
        private final String name;
        private final boolean global;

        public Skill(String name, boolean global) {
            this.name = name;
            this.global = global;
        }

        public String getName() {
            return name;
        }

        public boolean isGlobal() {
            return global;
        }

        @Override
        public String toString() {
            return (global ? "[全局] " : "[项目级] ") + name;
        }
    }

    public static List<Skill> parse(String output) {
        if (StringUtils.isBlank(output)) {
            return Collections.emptyList();
        }
        List<Skill> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String currentScope = "project";
        for (String line : output.split("\\R")) {
            String value = StringUtils.trimToEmpty(line);
            if (value.isEmpty() || value.startsWith("#")) {
                continue;
            }
            String lower = value.toLowerCase();
            if (lower.matches("global\\s*skills?[:：]?")) {
                currentScope = "global";
                continue;
            }
            if (lower.matches("project(?:\\s+level)?\\s*skills?[:：]?")) {
                currentScope = "project";
                continue;
            }
            int separator = value.indexOf(':');
            if (separator < 0) {
                separator = value.indexOf('：');
            }
            if (separator > 0) {
                String scope = value.substring(0, separator).trim().toLowerCase();
                if (scope.equals("global") || scope.equals("全局")) {
                    currentScope = "global";
                    value = value.substring(separator + 1).trim();
                } else if (scope.equals("project") || scope.equals("项目") || scope.equals("项目级")) {
                    currentScope = "project";
                    value = value.substring(separator + 1).trim();
                }
            }
            value = value.replaceFirst("^[-*]\\s+", "").trim();
            if (!value.isEmpty()) {
                String key = currentScope + "\\u0000" + value;
                if (seen.add(key)) {
                    result.add(new Skill(value, "global".equals(currentScope)));
                }
            }
        }
        return result;
    }

    public static List<String> buildArgs(List<Skill> skills) {
        List<String> args = new ArrayList<>();
        appendScopeArgs(args, skills, true);
        appendScopeArgs(args, skills, false);
        return args;
    }

    private static void appendScopeArgs(List<String> args, List<Skill> skills, boolean global) {
        boolean addedScope = false;
        for (Skill skill : skills) {
            if (skill.isGlobal() != global) {
                continue;
            }
            if (!addedScope) {
                args.add(global ? "--global" : "--project");
                addedScope = true;
            }
            args.add(skill.getName());
        }
    }
}
