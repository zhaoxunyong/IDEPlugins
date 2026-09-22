package com.zerofinance.zerogit.eclipse.actions;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;

import org.apache.commons.lang.StringUtils;

import com.zerofinance.zerogit.eclipse.ui.UserInteraction;

public class GetApiVersionHandler extends AbstractZeroGitHandler {
    private static final Pattern FLATTEN_MAVEN_PLUGIN_PATTERN = Pattern.compile("<artifactId>\\s*flatten-maven-plugin\\s*</artifactId>");
    /**
     * 查詢指定 API 制品的版本。
     *
     * @param event Eclipse 命令執行事件
     * @return 無回傳值
     * @throws ExecutionException 無法取得專案或 Git 倉庫根目錄時拋出
     */
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IProject project = requireProject(event);
        String repoRoot = requireRepositoryRoot(event);
        UserInteraction.SelectionInput selection = ui().chooseValueOrManualInput(
                shell(event),
                "ZeroGit: Get API Version",
                "选择要查询版本的 API 模块（默认选择第一个）",
                findFlattenMavenModules(repoRoot),
                "或手动输入 artifactId 或 groupId:artifactId（输入后优先）");
        if (selection == null) {
            return null;
        }
        List<String> args = new ArrayList<String>();
        if (StringUtils.isNotBlank(selection.getManualInput())) {
            args.add(selection.getManualInput());
        } else if (!selection.getSelectedValues().isEmpty()) {
            args.add("--module");
            args.add(selection.getSelectedValues().get(0));
        } else {
            ui().showError(shell(event), "ZeroGit: Get API Version", "请选择至少一个 API 模块，或输入 Maven 坐标。");
            return null;
        }
        runScriptJob(
                shell(event),
                "Get API Version",
                project,
                buildRequest(repoRoot, "GetApiVersion.sh", args),
                false);
        return null;
    }

    private List<String> findFlattenMavenModules(String repoRoot) {
        List<String> modules = new ArrayList<String>();
        collectFlattenMavenModules(new File(repoRoot), new File(repoRoot), modules);
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
}
