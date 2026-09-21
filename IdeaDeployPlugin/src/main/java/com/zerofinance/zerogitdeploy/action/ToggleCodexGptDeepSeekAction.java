package com.zerofinance.zerogitdeploy.action;

import com.intellij.ide.BrowserUtil;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.zerofinance.zerogitdeploy.setting.ZeroGitDeploySetting;
import com.zerofinance.zerogitdeploy.tools.DeployCmdExecuter;
import com.zerofinance.zerogitdeploy.tools.ExecuteResult;
import com.zerofinance.zerogitdeploy.tools.MessagesUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;

public class ToggleCodexGptDeepSeekAction extends AnAction {
    private static final String SETUP_URL = "https://api-docs.deepseek.com/zh-cn/quick_start/agent_integrations/codex";
    private static final String SCRIPT_NAME = "CodexGptDeepSeek.sh";

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            return;
        }
        File codexHome = new File(System.getProperty("user.home"), ".codex");
        if (!new File(codexHome, "models.json").isFile()) {
            if (Messages.showYesNoDialog(project,
                    "未检测到 ~/.codex/models.json，请先参考 DeepSeek 文档进行初始化。",
                    "Codex GPT↔DeepSeek",
                    "打开初始化文档",
                    "取消",
                    Messages.getWarningIcon()) == Messages.YES) {
                BrowserUtil.browse(SETUP_URL);
            }
            return;
        }

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Codex 模型切换中...") {
            private ExecuteResult result;
            private Exception error;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    result = DeployCmdExecuter.exec(codexHome.getPath(), downloadScript(), Collections.emptyList(), true);
                } catch (Exception e) {
                    error = e;
                }
            }

            @Override
            public void onFinished() {
                if (error != null) {
                    MessagesUtils.showErrorWithDetails(project, "Codex 模型切换失败", "执行脚本失败。", MessagesUtils.buildDetailedErrorMessage(error));
                    return;
                }
                if (result == null || result.getCode() != 0) {
                    String detail = result == null ? "未返回执行结果。" : StringUtils.defaultString(result.getResult(), "(no output)");
                    MessagesUtils.showErrorWithDetails(project, "Codex 模型切换失败", "脚本执行失败。", detail);
                    return;
                }
                String message = StringUtils.trimToEmpty(result.getResult());
                MessagesUtils.showMessage(project,
                        StringUtils.isBlank(message) ? "Codex 模型已切换。" : message,
                        "Codex 模型已切换",
                        NotificationType.INFORMATION);
            }
        });
    }

    private String downloadScript() throws Exception {
        File script = new File(System.getProperty("java.io.tmpdir"), SCRIPT_NAME);
        try (InputStream input = new URL(ZeroGitDeploySetting.getScriptURL().replaceAll("/+$", "") + "/" + SCRIPT_NAME).openStream()) {
            FileUtils.copyInputStreamToFile(input, script);
        }
        return script.getPath();
    }
}
