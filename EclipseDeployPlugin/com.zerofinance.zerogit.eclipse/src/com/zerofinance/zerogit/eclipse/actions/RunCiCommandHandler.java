package com.zerofinance.zerogit.eclipse.actions;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;

import com.zerofinance.zerogit.eclipse.ci.GitlabCiCommandOption;
import com.zerofinance.zerogit.eclipse.ci.GitlabCiCommandReader;

public class RunCiCommandHandler extends AbstractZeroGitHandler {
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IProject project = requireProject(event);
        String repoRoot = requireRepositoryRoot(event);

        List<GitlabCiCommandOption> options;
        try {
            options = GitlabCiCommandReader.readFromRepoRoot(repoRoot);
        } catch (Exception e) {
            throw new ExecutionException("Failed to read .gitlab-ci.yml BASE_EXEC_CMD configuration.", e);
        }
        GitlabCiCommandOption selected = chooseOption(event, options);
        if (selected == null) {
            return null;
        }

        runRawCommandJob(shell(event), "Run CI Command", project, repoRoot, selected.getCommand(), null);
        return null;
    }

    private GitlabCiCommandOption chooseOption(ExecutionEvent event, List<GitlabCiCommandOption> options) {
        if (options == null || options.isEmpty()) {
            return null;
        }
        if (options.size() == 1) {
            return options.get(0);
        }

        List<String> labels = new ArrayList<String>();
        for (GitlabCiCommandOption option : options) {
            labels.add(option.getDisplayText());
        }
        String selected = ui().chooseValue(
                shell(event),
                "ZeroGit: GitLab CI",
                "请选择要执行的 BASE_EXEC_CMD",
                labels,
                labels.get(0));
        if (selected == null) {
            return null;
        }
        for (GitlabCiCommandOption option : options) {
            if (selected.equals(option.getDisplayText()) || selected.equals(option.getCommand())) {
                return option;
            }
        }
        return null;
    }
}
