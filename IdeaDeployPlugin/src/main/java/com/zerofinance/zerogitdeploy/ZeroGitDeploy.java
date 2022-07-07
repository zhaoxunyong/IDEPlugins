package com.zerofinance.zerogitdeploy;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Tool windows control.控制文本显示 2017/3/18 19:58
 */
public class ZeroGitDeploy implements ToolWindowFactory {

    private ToolWindow myToolWindow;
    private JPanel mPanel;
    private JTextArea txtContent;
    private JScrollPane mScrollPane;
    private JTextArea textArea1;

    /**
     * 创建控件内容 2017/3/24 09:02
     * @param project 项目
     * @param toolWindow 窗口
     */
    @Override
    public void createToolWindowContent(@NotNull Project project,
                                        @NotNull ToolWindow toolWindow) {
        myToolWindow = toolWindow;

        // 将显示面板添加到显示区 2017/3/18 19:57
//        ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
//        Content content = contentFactory.createContent(mPanel, DeployCmdExecuter.PLUGIN_TITLE, false);
//        toolWindow.getContentManager().addContent(content);

        // 禁止编辑 2017/3/18 19:57
        txtContent.setEditable(false);

        // 去除边框 2017/3/19 08:58
        txtContent.setBorder(BorderFactory.createLineBorder(Color.BLACK, 0));
        mScrollPane.setBorder(BorderFactory.createLineBorder(Color.BLACK, 0));
        mPanel.setBorder(BorderFactory.createLineBorder(Color.BLACK, 0));

        // 设置透明 2017/3/18 21:41
        mPanel.setOpaque(false);
        mScrollPane.setOpaque(false);
        mScrollPane.getViewport().setOpaque(false);
        txtContent.setOpaque(false);
    }

    @Override
    public void init(ToolWindow window) {

    }

}