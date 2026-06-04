package com.zerofinance.zerogit.eclipse.actions;

import java.util.Arrays;

import org.apache.commons.lang.StringUtils;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;

public class MavenChangeHandler extends AbstractZeroGitHandler {
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IProject project = requireProject(event);
        String projectRoot = requireMavenProjectRoot(event);
        String group = requireGroupSelection(shell(event));

        String changeType = ui().chooseValue(
                shell(event),
                "ZeroGit: Maven Change",
                "请选择 Maven 版本类型",
                Arrays.asList("release", "snapshot"),
                "release");
        if (changeType == null) {
            return null;
        }
        String normalizedType = StringUtils.trimToEmpty(changeType).toLowerCase();
        if (!"release".equals(normalizedType) && !"snapshot".equals(normalizedType)) {
            ui().showError(shell(event), "ZeroGit: Maven Change", "仅支持选择 release 或 snapshot。");
            return null;
        }

        String currentPomVersion = readPomVersion(projectRoot);
        String suggestedVersion = flowService().suggestMavenVersion(currentPomVersion, normalizedType);
        if ("release".equals(normalizedType) && suggestedVersion == null) {
            ui().showError(shell(event), "ZeroGit: Maven Change", "你只能基于RC或SNAPSHOT进行操作");
            return null;
        }

        String mavenVersion = ui().promptText(
                shell(event),
                "ZeroGit: Maven Change",
                "请输入 Maven 版本号",
                StringUtils.defaultString(suggestedVersion));
        if (mavenVersion == null) {
            return null;
        }

        String validationMessage = flowService().validateMavenVersion(mavenVersion, normalizedType);
        if (validationMessage != null) {
            ui().showError(shell(event), "ZeroGit: Maven Change", validationMessage);
            return null;
        }

        runScriptJob(
                shell(event),
                "Maven Change",
                project,
                buildRequest(projectRoot, "MavenChange.sh", flowService().buildMavenChangeArgs(group, mavenVersion)),
                false);
        return null;
    }
}
