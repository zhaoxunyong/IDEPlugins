package com.zerofinance.zerogitdeploy.tools;

import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 解析 GetSkills.sh 输出并构造 UpdateSkills.sh 参数。 */
public final class SkillUpdateSupport {
    private SkillUpdateSupport() {
    }

    public static final class Skill {
        private final String name;
        private final String action;
        private final boolean global;

        public Skill(String name, String action, boolean global) {
            this.name = name;
            this.action = action;
            this.global = global;
        }

        public String getName() {
            return name;
        }

        public String getAction() {
            return action;
        }

        public boolean isGlobal() {
            return global;
        }

        @Override
        public String toString() {
            return ("update".equals(action) ? "Update" : "Delete") +
                    " · " + (global ? "全局 skill" : "项目级 skill") + " · " + name;
        }
    }

    public static List<Skill> parse(String output) {
        if (StringUtils.isBlank(output)) {
            return Collections.emptyList();
        }
        List<Skill> result = new ArrayList<>();
        Map<String, String> actions = new HashMap<>();
        String[] lines = output.split("\\R");
        for (int index = 0; index < lines.length; index++) {
            String value = StringUtils.trimToEmpty(lines[index]);
            if (value.isEmpty() || value.startsWith("#")) {
                continue;
            }
            String[] fields = value.split("\\s+");
            String action = fields[0];
            if (!"update".equals(action) && !"delete".equals(action)) {
                throw invalid(index, "动作无效: " + action);
            }
            if (fields.length < 2 || (!"global".equals(fields[1]) && !"project".equals(fields[1]))) {
                throw invalid(index, "作用域无效: " + (fields.length < 2 ? "" : fields[1]));
            }
            if (fields.length < 3) {
                throw invalid(index, "缺少 skill");
            }
            String scope = fields[1];
            for (int field = 2; field < fields.length; field++) {
                String name = fields[field];
                if (!name.matches("[a-zA-Z0-9][a-zA-Z0-9._-]*")) {
                    throw invalid(index, "skill 名称无效: " + name);
                }
                String key = scope + "\u0000" + name;
                String existingAction = actions.get(key);
                if (existingAction != null && !existingAction.equals(action)) {
                    throw invalid(index, "skill 动作冲突: " + name);
                }
                if (existingAction == null) {
                    actions.put(key, action);
                    result.add(new Skill(name, action, "global".equals(scope)));
                }
            }
        }
        return result;
    }

    private static IllegalArgumentException invalid(int lineIndex, String message) {
        return new IllegalArgumentException("GetSkills.sh 第 " + (lineIndex + 1) + " 行" + message);
    }

    public static List<String> buildArgs(List<Skill> skills) {
        List<String> args = new ArrayList<>();
        appendArgs(args, skills, "update", true);
        appendArgs(args, skills, "update", false);
        appendArgs(args, skills, "delete", true);
        appendArgs(args, skills, "delete", false);
        return args;
    }

    private static void appendArgs(List<String> args, List<Skill> skills, String action, boolean global) {
        boolean addedGroup = false;
        for (Skill skill : skills) {
            if (!action.equals(skill.getAction()) || skill.isGlobal() != global) {
                continue;
            }
            if (!addedGroup) {
                args.add("--" + action + "-" + (global ? "global" : "project"));
                addedGroup = true;
            }
            args.add(skill.getName());
        }
    }
}
