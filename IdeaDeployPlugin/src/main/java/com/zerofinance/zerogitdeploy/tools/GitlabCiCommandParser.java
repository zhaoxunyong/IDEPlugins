package com.zerofinance.zerogitdeploy.tools;

import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GitlabCiCommandParser {

    private GitlabCiCommandParser() {
    }

    public static List<GitlabCiCommandOption> parse(String yamlText) {
        Object loaded;
        try {
            loaded = new Yaml().load(yamlText == null ? "" : yamlText);
        } catch (Exception e) {
            throw new IllegalArgumentException(".gitlab-ci.yml 解析失败：" + e.getMessage(), e);
        }
        if (!(loaded instanceof Map)) {
            throw new IllegalArgumentException("未找到 BASE_EXEC_CMD 配置");
        }

        Map<String, GitlabCiCommandOption> options = new LinkedHashMap<>();
        Map<?, ?> root = (Map<?, ?>) loaded;

        append(options, "", root.get("BASE_EXEC_CMD"));

        Object globalVariables = root.get("variables");
        if (globalVariables instanceof Map) {
            append(options, "variables", ((Map<?, ?>) globalVariables).get("BASE_EXEC_CMD"));
        }

        for (Map.Entry<?, ?> entry : root.entrySet()) {
            if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof Map)) {
                continue;
            }
            Object variables = ((Map<?, ?>) entry.getValue()).get("variables");
            if (variables instanceof Map) {
                append(options, (String) entry.getKey(), ((Map<?, ?>) variables).get("BASE_EXEC_CMD"));
            }
        }

        if (options.isEmpty()) {
            throw new IllegalArgumentException("未找到 BASE_EXEC_CMD 配置");
        }

        return new ArrayList<>(options.values());
    }

    private static void append(Map<String, GitlabCiCommandOption> options, String sourceLabel, Object rawValue) {
        if (!(rawValue instanceof String)) {
            return;
        }

        for (String segment : String.valueOf(rawValue).split(";")) {
            String command = segment.trim();
            if (command.isEmpty() || options.containsKey(command)) {
                continue;
            }
            options.put(command, new GitlabCiCommandOption(sourceLabel, command));
        }
    }
}
