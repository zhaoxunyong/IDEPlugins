package com.zerofinance.zerogitdeploy;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class ZeroGitDeploy implements ToolWindowFactory {

    private ToolWindow myToolWindow;
    private JPanel mPanel;
    private JTextArea txtContent;
    private JScrollPane mScrollPane;
    private JTextArea textArea1;

    @Override
    public void createToolWindowContent(@NotNull Project project,
                                        @NotNull ToolWindow toolWindow) {
        myToolWindow = toolWindow;

//        ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
//        Content content = contentFactory.createContent(mPanel, DeployCmdExecuter.PLUGIN_TITLE, false);
//        toolWindow.getContentManager().addContent(content);

        txtContent.setEditable(false);

        txtContent.setBorder(BorderFactory.createLineBorder(Color.BLACK, 0));
        mScrollPane.setBorder(BorderFactory.createLineBorder(Color.BLACK, 0));
        mPanel.setBorder(BorderFactory.createLineBorder(Color.BLACK, 0));

        mPanel.setOpaque(false);
        mScrollPane.setOpaque(false);
        mScrollPane.getViewport().setOpaque(false);
        txtContent.setOpaque(false);
    }

    @Override
    public void init(ToolWindow window) {

    }

}