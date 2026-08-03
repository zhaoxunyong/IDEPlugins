package com.zerofinance.zerogit.eclipse.actions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;

import com.zerofinance.zerogit.eclipse.exec.CommandRequest;
import com.zerofinance.zerogit.eclipse.exec.CommandResult;
import com.zerofinance.zerogit.eclipse.flow.SkillUpdateSupport;

public class UpdateSkillsHandler extends AbstractZeroGitHandler {
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IProject project = requireProject(event);
        String repoRoot = requireRepositoryRoot(event);
        CommandResult listResult = runScriptNow(buildRequest(repoRoot, "GetSkills.sh", Collections.<String>emptyList()));
        if (!listResult.isSuccess()) {
            throw new ExecutionException("GetSkills.sh 执行失败：" + listResult.getOutput());
        }

        List<SkillUpdateSupport.Skill> skills = SkillUpdateSupport.parse(listResult.getOutput());
        if (skills.isEmpty()) {
            ui().showWarning(shell(event), "ZeroGit: Update Skills", "GetSkills.sh 未返回可更新的 skill 列表。");
            return null;
        }

        List<String> labels = new ArrayList<>();
        Map<String, SkillUpdateSupport.Skill> byLabel = new HashMap<>();
        for (SkillUpdateSupport.Skill skill : skills) {
            labels.add(skill.toString());
            byLabel.put(skill.toString(), skill);
        }
        List<String> selectedLabels = ui().chooseValues(
                shell(event),
                "ZeroGit: Update Skills",
                "请选择要执行的 Skills（动作由 GetSkills.sh 配置，默认全选）",
                labels);
        if (selectedLabels.isEmpty()) {
            return null;
        }

        List<SkillUpdateSupport.Skill> selected = new ArrayList<>();
        for (String label : selectedLabels) {
            SkillUpdateSupport.Skill skill = byLabel.get(label);
            if (skill != null) {
                selected.add(skill);
            }
        }
        List<String> args = SkillUpdateSupport.buildArgs(selected);
        if (!ui().confirm(shell(event), "ZeroGit Confirm", buildConfirmation(repoRoot, args))) {
            return null;
        }
        runScriptJob(shell(event), "Update Skills", project, buildRequest(repoRoot, "UpdateSkills.sh", args), false);
        return null;
    }

    private String buildConfirmation(String repoRoot, List<String> args) {
        return "即将在项目中执行 UpdateSkills.sh：\n\n工作目录：" + repoRoot +
                "\n参数：" + (args.isEmpty() ? "(无)" : String.join(" ", args)) + "\n\n是否继续执行？";
    }
}
