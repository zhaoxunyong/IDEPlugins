package com.zerofinance.zerogitdeploy.tools;

import com.google.common.base.Joiner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.zerofinance.zerogitdeploy.setting.ZeroGitDeploySetting;
import org.apache.commons.exec.*;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * DeployPluginHelper
 * 
 * <p>
 * <a href="PluginUtils.java"><i>View Source</i></a>
 * </p>
 * 
 * @author zhaoxunyong
 * @version 3.0
 * @since 1.0
 */
public class DeployCmdExecuter {
    public final static String PLUGIN_ID = "GitDeployPlugin";
    public final static String PLUGIN_TITLE = "Git Deploy";

    public static ExecuteResult exec(String workHome, String command, List<String> params, boolean isBatchScript) throws InterruptedException, IOException {
        return exec(null, workHome, command, params, isBatchScript);
    }

    public static ExecuteResult execDirect(String workHome, String command, List<String> parameters) throws IOException, InterruptedException {
        return execDirect(null, workHome, command, parameters);
    }

    public static ExecuteResult execDirect(final ConsoleView console, String workHome, String command, List<String> parameters) throws IOException, InterruptedException {
        CommandLine cmdLine = new CommandLine(command);
        if (parameters != null && !parameters.isEmpty()) {
            for (String p : parameters) {
                if (StringUtils.isNotBlank(p)) {
                    cmdLine.addArgument(p);
                }
            }
        }
        Executor executor = new DefaultExecutor();
        executor.setWorkingDirectory(new File(workHome));
        int[] codes = new int[256];
        for (int i = 0; i < 256; i++) {
            codes[i] = i;
        }
        executor.setExitValues(codes);

        if (console != null) {
            StringBuilder msgs = new StringBuilder();
            executor.setStreamHandler(new PumpStreamHandler(new LogOutputStream() {
                @Override
                protected void processLine(String line, int level) {
                    msgs.append(line).append("\n");
                    console.print(line + "\n", ConsoleViewContentType.NORMAL_OUTPUT);
                }
            }, new LogOutputStream() {
                @Override
                protected void processLine(String line, int level) {
                    msgs.append(line).append("\n");
                    console.print(line + "\n", ConsoleViewContentType.NORMAL_OUTPUT);
                }
            }));
            try {
                int code = executor.execute(cmdLine);
                return new ExecuteResult(code, msgs.toString());
            } catch (ExecuteException e) {
                return new ExecuteResult(-1, e.getMessage());
            }
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
        PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream, errorStream);
        executor.setStreamHandler(streamHandler);
        try {
            int code = executor.execute(cmdLine);
            String msg = mergeStdoutAndStderr(outputStream.toString("utf-8"), errorStream.toString("utf-8"));
            return new ExecuteResult(code, msg);
        } catch (ExecuteException e) {
            return new ExecuteResult(-1, e.getMessage());
        }
    }
    
    public static ExecuteResult exec(final ConsoleView console, String workHome, String command, List<String> parameters, boolean isBatchScript) throws IOException, InterruptedException {
        String debug = ZeroGitDeploySetting.isDebug() ? "-x" : "";

        CommandLine cmdLine = null;
        if(SystemUtils.IS_OS_WINDOWS) {
            // For windows
            cmdLine = new CommandLine(ZeroGitDeploySetting.getGitHome()+"\\bin\\bash.exe");
            if(isBatchScript) {
                // Batch script
                if(StringUtils.isNotBlank(debug)) {
                    cmdLine.addArgument(debug);
                }
                cmdLine.addArgument(command);
                if(parameters!=null && !parameters.isEmpty()) {
                    for(String p : parameters) {
                        cmdLine.addArgument(p);
                    }
                }
            } else {
                // single script
//            	String params = Joiner.on(" ").join(parameters);
//                cmdLine.addArgument("-c");
//                cmdLine.addArgument("\""+command+" "+params+"\"");

                // Supported using pipe in commands: can't contain "quotation mark" in pipe
                String params = parameters == null ? "" : Joiner.on(" ").join(parameters);
                String myActualCommand = command+" "+params;
//        		cmdLine = new CommandLine(FileHandlerUtils.getGitHome()+"\\bin\\bash.exe").addArgument("-c");
                cmdLine.addArgument("-c");
                // set handleQuoting = false so our command is taken as it is
                cmdLine.addArgument(myActualCommand, false);
            }
        } else {
            cmdLine = new CommandLine("bash");
            // For Unix
            if (isBatchScript) {
                // Batch script
                if(StringUtils.isNotBlank(debug)) {
                    cmdLine.addArgument(debug);
                }
                cmdLine.addArgument(command);
                if (parameters != null && !parameters.isEmpty()) {
                    for (String p : parameters) {
                        if (StringUtils.isNotBlank(p)) {
                            cmdLine.addArgument(p);
                        }
                    }
                }
            } else {
                // Supported using pipe in commands: can't contain "quotation mark" in pipe
                String myActualCommand = command;
                if (parameters != null && !parameters.isEmpty()) {
                    String params = Joiner.on(" ").join(parameters);
                    myActualCommand += " " + params;
                }
                cmdLine.addArgument("-c");
                // set handleQuoting = false so our command is taken as it is
                cmdLine.addArgument(myActualCommand, false);
            }
        }
        // CommandLine cmdLine = CommandLine.parse(shell);
        Executor executor = new DefaultExecutor();
        executor.setWorkingDirectory(new File(workHome));
        // Ignore all error code
        int successSartCode = 0;
        int sucessEndCode = 255;
        int[] codes = new int[sucessEndCode-successSartCode+1];
        for(int i=successSartCode;i<=sucessEndCode;i++) {
        	codes[i] = i;
        }
    	executor.setExitValues(codes);
//        String out = "";
        ExecuteResult executeResult;
        StringBuilder msgs = new StringBuilder();
        if(console!=null) {
            executor.setStreamHandler(new PumpStreamHandler(new LogOutputStream() {

                @Override
                protected void processLine(String line, int level) {
                    msgs.append(line+"\n");
                    console.print(line+"\n", ConsoleViewContentType.NORMAL_OUTPUT);
                }
            }, new LogOutputStream() {

                @Override
                protected void processLine(String line, int level) {
                    String normalized = normalizeStderrLine(line);
                    if (StringUtils.isNotBlank(normalized)) {
                        msgs.append(normalized).append("\n");
                    }
                    console.print(line+"\n", ConsoleViewContentType.NORMAL_OUTPUT);
                }
            }));
            
//            int code = executor.execute(cmdLine);
            try {
            	int code = executor.execute(cmdLine);
//                String msg = code == 0 ? okMsgs.toString() : errMsgs.toString();
//                executeResult = new ExecuteResult(code, msg);
                executeResult = new ExecuteResult(code, msgs.toString());
            }catch(ExecuteException e) {
                e.printStackTrace();
                executeResult = new ExecuteResult(-1, e.getMessage());
            }
        } else {
        	// return the output
        	ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
            PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream,errorStream);
            executor.setStreamHandler(streamHandler);
            try {
            	int code = executor.execute(cmdLine);
                String msg = mergeStdoutAndStderr(outputStream.toString("utf-8"), errorStream.toString("utf-8"));
                executeResult = new ExecuteResult(code, msg);
            }catch(ExecuteException e) {
                e.printStackTrace();
                executeResult = new ExecuteResult(-1, e.getMessage());
            }
        }
        return executeResult;
    }

    private static String mergeStdoutAndStderr(String stdout, String stderr) {
        String normalizedErr = filterDebugTrace(stderr);
        if (StringUtils.isBlank(stdout)) {
            return StringUtils.defaultString(normalizedErr);
        }
        if (StringUtils.isBlank(normalizedErr)) {
            return stdout;
        }
        return stdout + "\n" + normalizedErr;
    }

    private static String normalizeStderrLine(String line) {
        if (line == null) {
            return "";
        }
        if (ZeroGitDeploySetting.isDebug() && line.trim().startsWith("+")) {
            return "";
        }
        return line;
    }

    private static String filterDebugTrace(String stderr) {
        if (StringUtils.isBlank(stderr)) {
            return stderr;
        }
        if (!ZeroGitDeploySetting.isDebug()) {
            return stderr;
        }
        StringBuilder sb = new StringBuilder();
        for (String line : stderr.split("\\R")) {
            if (!line.trim().startsWith("+")) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString().trim();
    }
}
