package com.zerofinance.zerogit.eclipse.actions;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;

import com.zerofinance.zerogit.eclipse.exec.CommandResult;

public class StartNewApiHandler extends AbstractZeroGitHandler {
    private static final Pattern FLATTEN_MAVEN_PLUGIN_PATTERN = Pattern.compile("<artifactId>\\s*flatten-maven-plugin\\s*</artifactId>");

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IProject project = requireProject(event);
        String repoRoot = requireRepositoryRoot(event);
        List<String> modulePaths = findFlattenMavenModules(repoRoot);
        if (modulePaths.isEmpty()) {
            ui().showError(shell(event), "ZeroGit: Start New Api", "未找到包含 flatten-maven-plugin 的 Maven 模块。");
            return null;
        }
        if (!ui().confirm(shell(event), "ZeroGit: Start New Api", "以下 API 模块将全部发布：\n" + StringUtils.join(modulePaths, "\n"))) {
            return null;
        }
        String publicationKind = ui().chooseValue(shell(event), "ZeroGit: Start New Api", "请选择 API 发布类型",
                Arrays.asList("release", "snapshot"), "release");
        if (StringUtils.isBlank(publicationKind)) {
            return null;
        }
        List<String> args = new ArrayList<String>();
        args.add("--publish");
        String firstModulePath = modulePaths.get(0);
        CommandResult versionResult = runScriptNow(buildRequest(repoRoot, "StartNewApi.sh", Arrays.asList("--suggest", firstModulePath, publicationKind)));
        if (!versionResult.isSuccess()) {
            throw new ExecutionException("获取 API 建议版本失败：" + versionResult.getOutput());
        }
        String version = ui().promptText(shell(event), "ZeroGit: Start New Api", "请输入 API 版本号（可手动调整）",
                lastNonBlankLine(versionResult.getOutput()));
        if (StringUtils.isBlank(version)) {
            return null;
        }
        String normalizedVersion = version.trim();
        if (!isValidVersion(normalizedVersion, publicationKind)) {
            throw new ExecutionException("版本必须为 x.y.z 或 x.y.z-SNAPSHOT，且必须与发布类型一致。");
        }
        for (String modulePath : modulePaths) {
            args.add(modulePath);
            args.add(normalizedVersion);
        }
        runScriptJob(
                shell(event),
                "Start New Api",
                project,
                buildRequest(repoRoot, "StartNewApi.sh", args),
                false);
        return null;
    }

    private List<String> findFlattenMavenModules(String repoRoot) {
        List<String> modules = new ArrayList<String>();
        collectFlattenMavenModules(new File(repoRoot), new File(repoRoot), modules);
        if (modules.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        modules.sort((left, right) -> {
            boolean leftApi = left.toLowerCase().endsWith("-api");
            boolean rightApi = right.toLowerCase().endsWith("-api");
            if (leftApi != rightApi) {
                return leftApi ? -1 : 1;
            }
            return left.compareTo(right);
        });
        return modules;
    }

    private void collectFlattenMavenModules(File root, File current, List<String> modules) {
        File pomFile = new File(current, "pom.xml");
        if (pomFile.isFile()) {
            try {
                if (FLATTEN_MAVEN_PLUGIN_PATTERN.matcher(new String(Files.readAllBytes(pomFile.toPath()), StandardCharsets.UTF_8)).find()) {
                    String relative = root.toPath().relativize(current.toPath()).toString().replace(File.separatorChar, '/');
                    modules.add(StringUtils.isBlank(relative) ? "." : relative);
                }
            } catch (Exception ignored) {
                // 忽略无法读取的 POM，让用户选择其余有效模块。
            }
        }
        File[] children = current.listFiles(File::isDirectory);
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (!".git".equals(child.getName()) && !"target".equals(child.getName())) {
                collectFlattenMavenModules(root, child, modules);
            }
        }
    }

    private boolean isValidVersion(String version, String publicationKind) {
        boolean snapshot = version.matches("^\\d+\\.\\d+\\.\\d+-SNAPSHOT$");
        return version.matches("^\\d+\\.\\d+\\.\\d+(-SNAPSHOT)?$") && ("snapshot".equals(publicationKind) == snapshot);
    }

    private String lastNonBlankLine(String output) throws ExecutionException {
        String[] lines = StringUtils.defaultString(output).split("\\R");
        for (int index = lines.length - 1; index >= 0; index--) {
            if (StringUtils.isNotBlank(lines[index])) {
                return lines[index].trim();
            }
        }
        throw new ExecutionException("StartNewApi.sh 未返回建议版本。");
    }
}
