package com.zerofinance.zerogitdeploy.handler;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBList;
import org.apache.commons.lang.StringUtils;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.HashMap;
import java.util.Map;

/**
 * https://www.jianshu.com/p/6d4b8821203a
 *
 * <p>
 * <a href="DependenciesDialogWrapper.java"><i>View Source</i></a>
 *
 * @author Dave.zhao
 * Date: 12/16/2022 6:32 PM
 */
public class DependenciesDialogWrapper extends DialogWrapper {

    private final Map<String, String> map;

    private Map<String, JTextField> textFields = new HashMap<>();

    public DependenciesDialogWrapper(Map<String, String> map) {
        super(true); // use current window as parent
        setTitle("请确认项目依赖版本");
        this.map = map;
        init();
    }

    public Map<String, JTextField> getTextFields() {
        return textFields;
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JPanel component = buildJpanel();
        component.setPreferredSize(new Dimension(850, 300));
        // 设置布局
        component.setLayout(new BorderLayout());
        // 设置窗口最小尺寸
        component.setMinimumSize(new Dimension(850, 300));
        component.setVisible(true);
        // 设置窗口尺寸是否固定不变
        return component;
    }

    private JPanel buildJpanel() {
        // 面板组件
        JPanel dbPanel = new JPanel();
        dbPanel.setLayout(null);
        // 下拉框
//        dbPanel.add(buildJLabel("数据库", 10, 20, 80, 35));
//        String dbs[] = {"mysql", "oracle", "sqlserver"};
//        dbPanel.add(buildJComboBox("mysql", "mysql", dbs, 100, 100, 20, 165, 35));

        // 文本框
//        int x1 = 10;
        int height = 35;
        int width = 200;
        int y = 50;
        int x1 = 10;
        int x2 = 220;
        int x3 = x2+width;

        dbPanel.add(buildJTextArea("\"项目当前版本\"为当前项目所依赖的其他项目的版本号。\"依赖项目当前版本\"为所依赖的项目本地环境的当前版本。\n插件会根据\"项目当前版本\"自动替换掉当前项目中所依赖的版本信息。", x1, 0, 810, 50));
        y+=15;
        dbPanel.add(buildJLabel("项目依赖模块", x1, y, width, height));
        dbPanel.add(buildJLabel("项目当前版本", x2, y, width, height));
        dbPanel.add(buildJLabel("依赖项目当前版本", x3, y, width, height));
        y+=height;
        int index = 0;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            String currValue = StringUtils.substringBefore(value, "/");
            String latestValue = StringUtils.substringAfter(value, "/");
            dbPanel.add(buildJLabel(key.replaceAll("[._-]version",""), x1, y, width, height));
            JTextField currVersion = buildJTextField(currValue, "currVersion"+index, 20, x2, y, width, height);
            JTextField latestVersion = buildJTextField(latestValue, "latestVersion+"+index, 20, x3, y, width, height);
            y+=height;

            dbPanel.add(currVersion);
            dbPanel.add(latestVersion);
            textFields.put(key, currVersion);
            index++;
        }

//        // 密码
//        dbPanel.add(buildJLabel("密码", 10, 80, 80, 35));
//        JPasswordField dbPassWord = buildJPasswordField("dbPassWord", "dbPassWord", 20, 100, 80, 165, 35);
//        dbPanel.add(dbPassWord);
//
//        // 添加按钮，并为按钮绑定事件监听
//        JButton saveButton = buildJButton("保存", 400, 80, 80, 35);
//        addActionListener(saveButton);
//        dbPanel.add(saveButton);


        return dbPanel;
    }



//    private void addActionListener(JButton saveButton) {
//        // 为按钮绑定监听器
//        saveButton.addActionListener(new ActionListener() {
//            @Override
//            public void actionPerformed(ActionEvent e) {
//                // 对话框
//                JOptionPane.showMessageDialog(null, "保存成功！");
//            }
//        });
//    }

//    private JButton buildJButton(String name, int x, int y, int width, int height) {
//        JButton button = new JButton(name);
//        button.setBounds(x, y, width, height);
//        return button;
//    }
//
//    private JPasswordField buildJPasswordField(String defaultValue, String name, int columns, int x, int y, int width, int height) {
//        JPasswordField jPasswordField = new JPasswordField(columns);
//        jPasswordField.setText(defaultValue);
//        jPasswordField.setName(name);
//        jPasswordField.setBounds(x, y, width, height);
//        return jPasswordField;
//    }

    private JTextField buildJTextField(String defaultValue, String name, int columns, int x, int y, int width, int height) {
        JTextField text = new JTextField(columns);
        text.setText(defaultValue);
        text.setName(name);
        text.setBounds(x, y, width, height);
        return text;
    }

//    private JComboBox buildJComboBox(Object selectedItem, String name, String[] elements, int selectedIndex, int x, int y, int width, int height) {
//        DefaultComboBoxModel codeTypeModel = new DefaultComboBoxModel();
//        // elements 下拉框中的选项
//        for (String element : elements) {
//            codeTypeModel.addElement(element);
//        }
//        JComboBox codeTypeBox = new JComboBox(codeTypeModel);
//        codeTypeBox.setName(name);
//        // 默认选中的下拉框选项
//        codeTypeBox.setSelectedItem(selectedItem);
////        codeTypeBox.setSelectedItem(selectedIndex);
//        codeTypeBox.setBounds(x, y, width, height);
//        // 添加下拉框事件监听器
//        codeTypeBox.addItemListener(new ItemListener() {
//            @Override
//            public void itemStateChanged(ItemEvent e) {
//                if (e.getStateChange() == ItemEvent.SELECTED) {
//                    // 选择的下拉框选项
//                    System.out.println(e.getItem());
//                }
//            }
//        });
//        return codeTypeBox;
//    }

    private JLabel buildJLabel(String name, int x, int y, int width, int height) {
        JLabel label = new JLabel(name);
        label.setBounds(x, y, width, height);

        return label;
    }

    private JTextArea buildJTextArea(String name, int x, int y, int width, int height) {
        JTextArea area = new JTextArea(name);
        area.setBounds(x, y, width, height);

        return area;
    }

//    private void buildFrame(JComponent component) {
//        // 窗体容器
//        JFrame frame = new JFrame("数据同步工具");
//        frame.add(component);
//        //  JFrame.EXIT_ON_CLOSE  退出
//        //  JFrame.HIDE_ON_CLOSE  最小化隐藏
//        frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
//        // 设置布局
//        frame.getContentPane().setLayout(new BorderLayout());
//        frame.getContentPane().add(BorderLayout.CENTER, component);
//        // 设置窗口最小尺寸
//        frame.setMinimumSize(new Dimension(1060, 560));
//        // 调整此窗口的大小，以适合其子组件的首选大小和布局
//        frame.pack();
//        // 设置窗口相对于指定组件的位置
//        frame.setLocationRelativeTo(null);
//        frame.setVisible(true);
//        // 设置窗口尺寸是否固定不变
//        frame.setResizable(true);
//    }
//
//    public static void main(String[] args) {
//        Map<String, String> map = new HashMap<>();
//        map.put("aaa1", "bbb1");
//        map.put("aaa2", "bbb2");
//        new DependenciesDialogWrapper(map).showAndGet();
//    }
}