package com.zerofinance.zerogit.eclipse.flow;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

/**
 * 递归扫描 pom.xml，检测 dependency / plugin 版本（含 ${property} 引用）是否包含 -SNAPSHOT。
 * 与 IDEA 版 ZeroGitFlowHandler.pomContentContainsSnapshot 行为保持一致。
 */
public final class PomSnapshotSupport {
    private static final Pattern XML_COMMENT_PATTERN = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern PROPERTY_REFERENCE_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern PROPERTY_ENTRY_PATTERN = Pattern.compile("<([A-Za-z0-9_.-]+)>(.*?)</\\1>", Pattern.DOTALL);

    private PomSnapshotSupport() {
    }

    /** 目录树（跳过 .git）下任意 pom.xml 含 -SNAPSHOT 依赖/插件版本时返回 true。 */
    public static boolean containsSnapshot(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return false;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return false;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                if (!".git".equals(child.getName()) && containsSnapshot(child)) {
                    return true;
                }
            } else if ("pom.xml".equals(child.getName()) && pomFileContainsSnapshot(child)) {
                return true;
            }
        }
        return false;
    }

    private static boolean pomFileContainsSnapshot(File pom) {
        try {
            String content = new String(Files.readAllBytes(pom.toPath()), StandardCharsets.UTF_8);
            return pomContentContainsSnapshot(content);
        } catch (Exception e) {
            return false;
        }
    }

    static boolean pomContentContainsSnapshot(String content) {
        String normalized = XML_COMMENT_PATTERN.matcher(StringUtils.defaultString(content)).replaceAll("");
        Map<String, String> properties = extractPomProperties(normalized);
        return anyBlockVersionContainsSnapshot(normalized, "dependency", properties)
                || anyBlockVersionContainsSnapshot(normalized, "plugin", properties);
    }

    private static boolean anyBlockVersionContainsSnapshot(String content, String tagName, Map<String, String> properties) {
        for (String block : extractTagBlocks(content, tagName)) {
            String version = extractFirstTagValue(block, "version");
            if (StringUtils.isNotBlank(version)
                    && resolvePropertyReferences(version, properties, new HashSet<String>()).contains("-SNAPSHOT")) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, String> extractPomProperties(String content) {
        Map<String, String> properties = new HashMap<String, String>();
        for (String block : extractTagBlocks(content, "properties")) {
            Matcher matcher = PROPERTY_ENTRY_PATTERN.matcher(block);
            while (matcher.find()) {
                properties.put(matcher.group(1), StringUtils.trimToEmpty(matcher.group(2)));
            }
        }
        return properties;
    }

    private static List<String> extractTagBlocks(String content, String tagName) {
        List<String> blocks = new ArrayList<String>();
        Matcher matcher = tagPattern(tagName).matcher(content);
        while (matcher.find()) {
            blocks.add(matcher.group(1));
        }
        return blocks;
    }

    private static String extractFirstTagValue(String content, String tagName) {
        Matcher matcher = tagPattern(tagName).matcher(content);
        return matcher.find() ? StringUtils.trimToEmpty(matcher.group(1)) : "";
    }

    private static Pattern tagPattern(String tagName) {
        return Pattern.compile("<" + tagName + "(?:\\s[^>]*)?>(.*?)</" + tagName + ">",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    }

    private static String resolvePropertyReferences(String value, Map<String, String> properties, Set<String> seen) {
        String normalized = StringUtils.defaultString(value).trim();
        if (normalized.isEmpty()) {
            return "";
        }
        Matcher matcher = PROPERTY_REFERENCE_PATTERN.matcher(normalized);
        StringBuffer resolved = new StringBuffer();
        while (matcher.find()) {
            String propertyName = matcher.group(1);
            String replacement = "";
            if (!seen.contains(propertyName) && properties.containsKey(propertyName)) {
                Set<String> nextSeen = new HashSet<String>(seen);
                nextSeen.add(propertyName);
                replacement = resolvePropertyReferences(properties.get(propertyName), properties, nextSeen);
            }
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }
}
