package com.zerofinance.zerogitdeploy.handler;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.terminal.JBTerminalWidget;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.zerofinance.zerogitdeploy.exception.DeployPluginException;
import com.zerofinance.zerogitdeploy.setting.ZeroGitDeploySetting;
import com.zerofinance.zerogitdeploy.tools.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.NameFileFilter;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
import org.apache.commons.lang3.CharSetUtils;
import org.apache.commons.lang3.RandomUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.ShellTerminalWidget;
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory;
import org.jetbrains.plugins.terminal.TerminalView;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * <p>
 * <a href="DeployPluginHandler.java"><i>View Source</i></a>
 * </p>
 *
 * @author zhaoxunyong
 * @version 3.0
 * @since 1.0
 */
@SuppressWarnings("restriction")
public final class DeployPluginHandler {
    private final Project project;

    private final String modulePath;

    private final String moduleName;

    private final VirtualFile moduleVirtualFile;

    private final AnActionEvent event;

    private final static String CHANGEVERSION_BAT = "./changeVersion.sh";

    private final static String RELEASE_BAT = "./release.sh";

    private final static String NEWBRANCH_BAT = "./newBranch.sh";

    private final static String GITCHECK_BAT = "./gitCheck.sh";

    private final static String COMMITED_LOGS_BAT = "./committedLogs.sh";

    private final static String ANY_TOOL_BAT = "./anyTool.sh";

    /**
     *
     */
    public DeployPluginHandler(AnActionEvent event) {
        this.event = event;
        this.project = event.getProject();

        VirtualFile vFile = event.getData(PlatformDataKeys.VIRTUAL_FILE);
        if (vFile == null) {
            // showMessage("Please pick up a valid module!", "Error", NotificationType.ERROR);
            // Messages.showErrorDialog("Please pick up a valid module!", "Error");
//            MessagesUtils.showMessage(project, "Please pick up a valid module!", "Error:", NotificationType.ERROR);
            throw new DeployPluginException("Please pick up a valid module!");
        }
        @Nullable Module module = ModuleUtil.findModuleForFile(vFile, project);
        this.moduleVirtualFile = ModuleRootManager.getInstance(module).getContentRoots()[0];

        this.modulePath = moduleVirtualFile.getPath();
        this.moduleName = new File(modulePath).getName();
//        System.out.println("module--->"+module.getName()+"/"+module.getModuleFilePath());
//        System.out.println("moduleRootPath--->"+moduleRootPath);
//        String selectedPath = vFile.getPath();
//        String rootProjectPath = CommandUtils.getRootProjectPath(modulePath);

        String gitHome = ZeroGitDeploySetting.getGitHome();
        if (SystemUtils.IS_OS_WINDOWS && StringUtils.isBlank(gitHome)) {
            throw new DeployPluginException("Please configure Git Home Path from:\n" +
                    "File -> Settings -> Git Deploy Settings");
        }
    }
    public boolean preCheck() {
        boolean isConfirm = true;
        ExecuteResult result = null;
        try {
            result = this.gitCheck();
        } catch (Exception e) {
            // Skipping check when the gitCheck.sh isn't existing
            MessagesUtils.showMessage(project, "Skipping checking the status of local git repository!", moduleName+": Information:", NotificationType.INFORMATION);
        }
        if (result != null && result.getCode() != 0) {
            if(ZeroGitDeploySetting.isSkipRepoChangeConfirmKey()) {
                isConfirm = Messages.showYesNoDialog(result.getResult(), moduleName+" repo is changed, want to continue?", "Confirm", "Cancel", Messages.getQuestionIcon()) == 0;
                if(!isConfirm) {
                    throw new DeployPluginException(result.getResult());
                }
            } else {
                throw new DeployPluginException(result.getResult());
            }
        }
        try {
            result = this.committedLogWarn();
            if (result != null && result.getCode() != 0) {
                throw new DeployPluginException(result.getResult());
            } else {
                isConfirm = Messages.showYesNoDialog("Making sure you don't forget merging some modified code.\n\n" + result.getResult(), moduleName+": Committed Log Confirm?", Messages.getQuestionIcon()) == 0;
            }
        } catch (Exception e) {
            // Skipping check when the committedLogs.sh isn't existing
            MessagesUtils.showMessage(project, "Skipping checking the latest committed logs!", moduleName+": Information:", NotificationType.INFORMATION);
        }
        return isConfirm;
    }
    
    /*private void clearConsole() {
        MessageConsole cs = DeployPluginHelper.findConsole();
        cs.clearConsole();
        cs.activate();
    }*/

    /**
     * http://www.vogella.com/tutorials/EclipseDialogs/article.html
     */
    private String input(String message, String title, String defaultValue) throws Exception {
        String input = Messages.showInputDialog(message, title, Messages.getInformationIcon(), defaultValue, new InputValidator() {

            @Override
            public boolean checkInput(@NlsSafe String inputString) {
                return true;
            }

            @Override
            public boolean canClose(@NlsSafe String inputString) {
                return true;
            }
        });
        if (StringUtils.isBlank(input)) {
            throw new DeployPluginException(message);
        }
        return input;
    }

    private String desc() throws Exception {
        String input = Messages.showInputDialog("Adding a message for git description", moduleName+": Description", Messages.getInformationIcon());
        if (StringUtils.isBlank(input)) {
            throw new DeployPluginException("Please input a available description.");
        }
        return input;
    }

    private int compareVersion(String version1, String version2) {
        if (version1 == null || version2 == null)
            return 0;

        String[] str1 = version1.split("\\.");
        String[] str2 = version2.split("\\.");

        for (int i = 0; i < str1.length || i < str2.length; ) {
            int n1 = i < str1.length ? Integer.parseInt(str1[i]) : 0;
            int n2 = i < str2.length ? Integer.parseInt(str2[i]) : 0;
            if (n1 > n2) return 1;
            else if (n1 < n2) return -1;
            else i++;
        }

        return 0;
    }

    private ExecuteResult gitCheck() throws Exception {
        String rootProjectPath = CommandUtils.getRootProjectPath(modulePath);
        String cmdFile = CommandUtils.processScript(rootProjectPath, GITCHECK_BAT);
//    	String projectName = project.getLocation().toFile().getName();
//    	String tempProjectFolder = FileHandlerUtils.getTempFolder()+"/"+projectName;
        List<String> params = Lists.newArrayList();
        ExecuteResult executeResult = DeployCmdExecuter.exec(rootProjectPath, cmdFile, params, true);
        return executeResult;
    }

    private ExecuteResult committedLogWarn() throws Exception {
        String rootProjectPath = CommandUtils.getRootProjectPath(modulePath);
        String cmdFile = CommandUtils.processScript(rootProjectPath, COMMITED_LOGS_BAT);
//    	String projectName = project.getLocation().toFile().getName();
//    	String tempProjectFolder = FileHandlerUtils.getTempFolder()+"/"+projectName;
        List<String> params = Lists.newArrayList();
        ExecuteResult executeResult = DeployCmdExecuter.exec(rootProjectPath, cmdFile, params, true);
        return executeResult;
    }

    private String[] lsRemote(String rootProjectPath) throws Exception {
        String command = "git";
        List<String> parameters = Lists.newArrayList("ls-remote");
//    	String command = "git ls-remote | grep -v '\\^{}' |  grep 'refs/heads' |awk '{print $NF}' | sed 's;refs/heads/;;g' | sort -t '.' -r -k 2 -V|egrep -i '(release|hotfix)$'";
//      List<String> parameters = Lists.newArrayList();
        ExecuteResult executeResult = DeployCmdExecuter.exec(rootProjectPath, command, parameters, false);
        // String result = CmdExecutor.exec(rootProjectPath, command, parameters);
//	        System.out.println("result----->"+result);
        int code = executeResult.getCode();
        String result = executeResult.getResult();
        if (code == 0 && !"".equals(result)) {
            return result.split("[\n|\r\n]");
        }
        return null;
    }

    private String getCurrentDevelopVersion(String[] results) throws Exception {
        String currentBranch = "";
        if (results != null) {
            String version2 = "0.0.0";
            for (String version : results) {
                if (version.endsWith(".x")) {
//		            String remoteBranchVersion = version.split("/")[2];
                    String remoteBranchVersion = StringUtils.substringAfterLast(version, "/");//.replace(".release", "").replace(".hotfix", "");
                    String version1 = remoteBranchVersion;
                    if (compareVersion(version1, version2) == 1) {
                        version2 = version1;
                        currentBranch = remoteBranchVersion;
                    }
                }
            }
        }
        return currentBranch;
    }

    @SuppressWarnings("unchecked")
    private String getRemoteVersion(String[] results) throws Exception {
        String currentBranch = "";
        if (results != null) {
            String version2 = "0.0.0";
            for (String version : results) {
                if (version.endsWith(".release") || version.endsWith(".hotfix")) {
//		            String remoteBranchVersion = version.split("/")[2];
                    String remoteBranchVersion = StringUtils.substringAfterLast(version, "/");//.replace(".release", "").replace(".hotfix", "");
                    String version1 = remoteBranchVersion.replaceAll("\\.release|\\.hotfix", "");
                    if (compareVersion(version1, version2) == 1) {
                        version2 = version1;
                        currentBranch = remoteBranchVersion;
                    }
                }
            }
        }
        return currentBranch;
    }

    @SuppressWarnings("unchecked")
    private String getMavenPomVersion(String rootProjectPath) throws Exception {
        String version = "";
        String pomFile = rootProjectPath + "/pom.xml";
        if (new File(pomFile).exists()) {
            List<String> value = FileUtils.readLines(new File(pomFile));
            for (String v : value) {
                if (v.indexOf("<version>") != -1) {
                    version = StringUtils.substringBetween(v, "<version>", "</version>");
                    break;
                }
            }
        } else {
//        	throw new DeployPluginException("It seems not a maven project, if you're using non-maven project, please handle it by vscode plugin!");
            String[] results = this.lsRemote(rootProjectPath);
            String currentDevelopVersion = getCurrentDevelopVersion(results);
            if (StringUtils.isBlank(currentDevelopVersion)) {
                throw new Exception("Please create a branch first.");
            }
            String[] currentBranchs = currentDevelopVersion.split("[.]");
            String b1 = currentBranchs[0];
            String b2 = currentBranchs[1];
            version = getRemoteVersion(results).replaceAll("\\.(release|hotfix)$", "");
            if (StringUtils.isBlank(version)) {
                // Not found the lastest release version, using the current branch instead
                version = b1 + "." + b2 + ".0";
            } else {
                String[] currentReleases = version.split("[.]");
                if (currentReleases.length >= 3) {
                    String p1 = currentReleases[0];
                    String p2 = currentReleases[1];
                    String p3 = currentReleases[2];
                    String compareBranch = b1 + b2;
                    String compareTag = p1 + p2;
                    if (!compareBranch.equals(compareTag)) {
                        version = b1 + "." + b2 + ".0";
                    } else {
                        int nextP3 = Integer.parseInt(p3) + 1;
                        version = p1 + '.' + p2 + '.' + nextP3;
                    }
                }

            }
        }
        return version;
    }

    private String haSnapshotVersion(List<String> value, String var) {
        for (String v : value) {
            if (v.indexOf(var) != -1 && v.indexOf("-SNAPSHOT") != -1) {
                return v.trim();
            }
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private String checkHasSnapshotVersion(String rootProjectPath) throws IOException {
        File dir = new File(rootProjectPath);
        Collection<File> files = FileUtils.listFiles(dir, FileFilterUtils.nameFileFilter("pom.xml"), DirectoryFileFilter.DIRECTORY);
        for (File f : files) {
            String pomFile = f.getPath();
            List<String> value = FileUtils.readLines(new File(pomFile));
            boolean isNew = false;
            for (String v : value) {
                if (v.indexOf("<dependency>") != -1) {
                    isNew = true;
                } else if (v.indexOf("</dependency>") != -1) {
                    isNew = false;
                }

                if (isNew && v.indexOf("version") != -1) {
                    // && v.indexOf("-SNAPSHOT") != -1
                    if (v.indexOf("${") != -1) {
                        String var = StringUtils.substringBetween(v, "${", "}");
                        String snapshotVar = haSnapshotVersion(value, var);
                        if (StringUtils.isNotBlank(snapshotVar)) {
                            return pomFile + "(" + snapshotVar + ")";
                        }
                    } else if (v.indexOf("-SNAPSHOT") != -1) {
                        return pomFile + "(" + v.trim() + ")";
                    }
                }
            }
        }
        return "";
    }



    private String getPreparingVersionFile() throws Exception {
        String preparingVersionFile = "";
        String rootProjectPath = CommandUtils.getRootProjectPath(modulePath);
        MavenDependency mavenDependency = MavenUtils.getDependencies(rootProjectPath);
        Map<String, String> dependencies = mavenDependency.getDependencies();
        System.out.println("dependencies===>"+dependencies);
        File pomFile = mavenDependency.getPomFile();
        if(dependencies!=null &&!dependencies.isEmpty()) {
            // Show dialog to change version
            DependenciesDialogWrapper dialogWrapper = new DependenciesDialogWrapper(dependencies);//.showAndGet();
            dialogWrapper.show();
            int exitCode = dialogWrapper.getExitCode();
            if(exitCode == DialogWrapper.OK_EXIT_CODE) {
                // ok
                preparingVersionFile = CommandUtils.getTempFolder()+File.separator+ UUID.randomUUID();
                Map<String, JTextField> textFields = dialogWrapper.getTextFields();
                // Checking text field
                textFields.forEach((key, textField)->{
                    String text = textField.getText();
                    System.out.println("key===>"+key);
                    System.out.println("textField===>"+text);
                    if(StringUtils.isBlank(text)) {
                        throw new DeployPluginException("Please give a available current dependency version.");
                    }
                });
                if(new File(preparingVersionFile).exists()) {
                    new File(preparingVersionFile).delete();
                }
                FileUtils.write(new File(preparingVersionFile), pomFile+"\n", StandardCharsets.UTF_8, true);

                for (Map.Entry<String, JTextField> entry : textFields.entrySet()) {
                    String key = entry.getKey();
                    JTextField textField = entry.getValue();
                    String text = textField.getText();
                    String value = key + ":" + text+"\n";
                    FileUtils.write(new File(preparingVersionFile), value, StandardCharsets.UTF_8, true);
                }
                preparingVersionFile = preparingVersionFile.replace("\\", "/");
                System.out.println("preparingVersionFile===>"+preparingVersionFile);
            }
        }
        return preparingVersionFile;
    }

    public void changeVersion() {
        try {
            String cmdFile = CommandUtils.processScript(modulePath, CHANGEVERSION_BAT);
            String rootProjectPath = CommandUtils.getRootProjectPath(modulePath);
//        String cmdName = FilenameUtils.getName(cmdFile);
            String pomVersion = getMavenPomVersion(rootProjectPath);
            String bPomVersion = StringUtils.substringBeforeLast(pomVersion, ".");
            String aPomVersion = StringUtils.substringAfterLast(pomVersion, ".").replace("-SNAPSHOT", "");
            aPomVersion = String.valueOf(Integer.parseInt(aPomVersion) + 1);
            String defaultValue = bPomVersion + "." + aPomVersion + "-SNAPSHOT";
            List<String> parameters = Lists.newArrayList();

            String version = input("Please input a available version", moduleName+": Input a version", defaultValue);
            parameters.add(version);

            if (parameters != null && !parameters.isEmpty()) {
                CmdBuilder cmdBuilder = new CmdBuilder(rootProjectPath, cmdFile, true, parameters);
                runJob(cmdBuilder);
            }
        } catch (Exception e) {
            throw new DeployPluginException(e.getMessage(), e);
        }
    }

    public void newBranch() {
        try {
            String cmdFile = CommandUtils.processScript(modulePath, NEWBRANCH_BAT);
            String rootProjectPath = CommandUtils.getRootProjectPath(modulePath);
//        String cmdName = FilenameUtils.getName(cmdFile);
            String pomVersion = getMavenPomVersion(rootProjectPath);
            // 1.5.6->1.6.x
            String bPomVersion = StringUtils.substringBeforeLast(pomVersion, "."); //1.5
            String a1PomVersion = StringUtils.substringBeforeLast(bPomVersion, "."); // 1
            String a2PomVersion = StringUtils.substringAfterLast(bPomVersion, "."); // 5
            a2PomVersion = String.valueOf(Integer.parseInt(a2PomVersion) + 1);
            String defaultValue = a1PomVersion + "." + a2PomVersion + ".x"; // 1.6.x
            List<String> parameters = Lists.newArrayList();
            String newBranch = input("Please input a available branch name", moduleName+": Input a branch name", defaultValue);
            parameters.add(newBranch);

            if (parameters != null && !parameters.isEmpty()) {
//            String projectPath = project.getLocation().toFile().getPath();
//            String rootProjectPath = getParentProject(projectPath, cmd);
                String desc = desc();
                parameters.add("\"" + desc + "\"");
                CmdBuilder cmdBuilder = new CmdBuilder(rootProjectPath, cmdFile, true, parameters);
                runJob(cmdBuilder);
            }
        } catch (Exception e) {
            throw new DeployPluginException(e.getMessage(), e);
        }
    }

    public void anyTool() {
        try {
            String rootProjectPath = CommandUtils.getRootProjectPath(modulePath);
//            VirtualFile vFile = event.getData(PlatformDataKeys.VIRTUAL_FILE);
            String cmdFile = CommandUtils.processScript(rootProjectPath, ANY_TOOL_BAT);
//        String cmdName = FilenameUtils.getName(cmdFile);
            // Using "projectPath" instead of "rootProjectPath"
            CmdBuilder cmdBuilder = new CmdBuilder(modulePath, cmdFile, true, Lists.newArrayList());
            boolean isConfirm = Messages.showYesNoDialog("Are you sure?",
                    moduleName+": Are you sure?", Messages.getQuestionIcon()) == 0;
            // boolean isConfirm = MessageDialog.openConfirm(shell, "Mybatis Gen Confirm?", project.getName() + " Mybatis Gen Confirm?");
            if (isConfirm) {
                runJob(cmdBuilder);
            }
        } catch (Exception e) {
            throw new DeployPluginException(e.getMessage(), e);
        }
    }

    public void release() {
        try {
            String preparingVersionFile = getPreparingVersionFile();
            String rootProjectPath = CommandUtils.getRootProjectPath(modulePath);

            boolean continute = true;
            try {
                String snapshotPath = checkHasSnapshotVersion(rootProjectPath);
                if (StringUtils.isNotBlank(snapshotPath)) {
                    continute = Messages.showYesNoDialog("There is a SNAPSHOT version in " + snapshotPath + ", when the version is released, it's suggested to replace it as release version. Do you want to continue?",
                            moduleName+": process confirm?",
                            Messages.getQuestionIcon()) == 0;
                    // continute = MessageDialog.openConfirm(shell, "process confirm?", "There is a SNAPSHOT version in "+snapshotPath+", when the version is released, it's suggested to replace it as release version. Do you want to continue?");
                }
            } catch (Exception e) {
                // do nothing
            }

            if (continute) {
                SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmm");
                String dateString = formatter.format(new Date());

                String releaseType = Messages.showEditableChooseDialog("Which release type do you want to pick?",
                        moduleName+": Choose", null, new String[]{"release", "hotfix"}, "release", null);
                if (!"release".equals(releaseType) && !"hotfix".equals(releaseType)) {
                    throw new DeployPluginException("Please pick up either release or hotfix option.");
                }
                String cmdFile = CommandUtils.processScript(rootProjectPath, RELEASE_BAT);
//		            String cmdName = FilenameUtils.getName(cmdFile);

                String pomVersion = getMavenPomVersion(rootProjectPath);
                String defaultValue = pomVersion.replace("-SNAPSHOT", "") + "." + releaseType;

                String inputtedVersion = input("Please input a available branch name", moduleName+": Input a branch name", defaultValue).trim();
                if (inputtedVersion.indexOf(" ") != -1) {
                    throw new DeployPluginException("The version is invalid.");
                }

                if (StringUtils.isNotBlank(inputtedVersion)) {
//		                String projectPath = project.getLocation().toFile().getPath();
//		                String rootProjectPath = getParentProject(projectPath, cmd);

                    String desc = desc();
                    List<String> parameters = Lists.newArrayList(inputtedVersion, dateString, "false", "\"" + desc + "\"", preparingVersionFile);
                    CmdBuilder cmdBuilder = new CmdBuilder(rootProjectPath, cmdFile, true, parameters);
                    runJob(cmdBuilder);
                }
            }
        } catch (Exception e) {
            throw new DeployPluginException(e.getMessage(), e);
        }
    }

    private void runJob(CmdBuilder cmdBuilder) {
        try {
            String modulePath = cmdBuilder.getWorkHome();
//            String title = new File(rootProjectPath).getName();
            String title = DeployCmdExecuter.PLUGIN_TITLE;
            if(ZeroGitDeploySetting.isRunnInTerminal()) {
                String command = cmdBuilder.getCommand();
                List<String> parameters = cmdBuilder.getParams();
                //Working via terminal
                String debug = ZeroGitDeploySetting.isDebug() ? "-x" : "";
                String moreDetails = ZeroGitDeploySetting.isMoreDetails() ? "-v" : "";
                command = "cd "+modulePath+" && bash " + debug + " " + moreDetails + " " + command;
                if (parameters != null && !parameters.isEmpty()) {
                    String params = Joiner.on(" ").join(parameters);
                    command += " " + params;
                }
                //System.out.println("command--->"+command);
                TerminalView terminalView = TerminalView.getInstance(project);
                ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID);
                if (window == null) {
                    return;
                }
                window.activate(null);
                ContentManager contentManager = window.getContentManager();
                Content content = contentManager.findContent(title);

                ShellTerminalWidget terminal = null;
                if (content == null) {
                    terminal = terminalView.createLocalShellWidget(project.getBasePath(), title);
                    terminal.executeCommand(command);
                } else {
                    Pair<Content, ShellTerminalWidget> pair = getSuitableProcess(content);
                    if (pair == null) {
                        //Messages.showInfoMessage("A terminal has been running", "Warnning");
                        MessagesUtils.showMessage(project, "A terminal has been running", moduleName+": Error:", NotificationType.ERROR);
                    } else {
                        pair.first.setDisplayName(title);
                        contentManager.setSelectedContent(pair.first);
                        terminal = pair.second;
                        terminal.executeCommand(command);
                    }
                }
                final ShellTerminalWidget finalTerminal = terminal;
                new Thread(()->{
                    try {
                        TimeUnit.SECONDS.sleep(1L);
                        while(finalTerminal.hasRunningCommands()) {
                            TimeUnit.MILLISECONDS.sleep(100L);
                        }
                        // Refresh the module space:
                        moduleVirtualFile.refresh(false, true);
                        MessagesUtils.showMessage(project, "Git Deploy Done, please check if Terminal has any error appeared!", moduleName+": Done", NotificationType.INFORMATION);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).start();
            } else {
                // https://stackoverflow.com/questions/51972122/intellij-plugin-development-print-in-console-window
                // https://intellij-support.jetbrains.com/hc/en-us/community/posts/206756385-How-to-make-a-simple-console-output
                // https://vimsky.com/examples/detail/java-class-com.intellij.execution.impl.ConsoleViewImpl.html
                // https://github.com/kungyutucheng/my_gradle_plugin
                //
                ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(DeployCmdExecuter.PLUGIN_ID);
//            toolWindow.show();
//            toolWindow.activate(null);
                toolWindow.setAvailable(true,null);
                toolWindow.show(null);

                ConsoleView consoleView = null;
                Content content = toolWindow.getContentManager().findContent(title);
                if(content == null) {
                    consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
                    content = toolWindow.getContentManager().getFactory().createContent(consoleView.getComponent(), title, false);
                    toolWindow.getContentManager().addContent(content);
                } else {
                    final JComponent component = content.getComponent();
                    if (component instanceof ConsoleViewImpl) {
                        consoleView = (ConsoleViewImpl)component;
                        consoleView.clear();
//                    ((ConsoleViewImpl)component).print(message, ConsoleViewContentType.ERROR_OUTPUT);
                    }
                }
                ConsoleView console = consoleView;

                // https://plugins.jetbrains.com/docs/intellij/general-threading-rules.html#background-processes-and-processcanceledexception
                Task.Backgroundable task = new Task.Backgroundable(project, "Processing...") {
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        String command = cmdBuilder.getCommand();
                        List<String> parameters = cmdBuilder.getParams();
                        ExecuteResult result = null;
                        try {
                            result = DeployCmdExecuter.exec(console, modulePath, command, parameters, true);
                            if (result != null && result.getCode() != 0) {
                                MessagesUtils.showMessage(project, "Something went wrong, please check the log messages!", moduleName+": Error", NotificationType.ERROR);
                            } else {
                                MessagesUtils.showMessage(project, "Git Deploy Ok!", moduleName+": Done", NotificationType.INFORMATION);
                            }
                            // Refresh the module space:
                            moduleVirtualFile.refresh(false, true);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                };
//                ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, new BackgroundableProcessIndicator(task));
                ProgressManager.getInstance().run(task);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private @Nullable Pair<Content, ShellTerminalWidget> getSuitableProcess(@NotNull Content content) {
        JBTerminalWidget widget = TerminalView.getWidgetByContent(content);
        /*if (!(widget instanceof ShellTerminalWidget)) {
            return null;
        }*/

        ShellTerminalWidget shellTerminalWidget = (ShellTerminalWidget) widget;
        if (!shellTerminalWidget.getTypedShellCommand().isEmpty() || shellTerminalWidget.hasRunningCommands()) {
            return null;
        }

        /*String currentWorkingDirectory = TerminalWorkingDirectoryManager.getWorkingDirectory(shellTerminalWidget, null);
        if (currentWorkingDirectory == null || !currentWorkingDirectory.equals(workingDirectory)) {
            return null;
        }*/

        return new Pair<>(content, shellTerminalWidget);
    }
}
