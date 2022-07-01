package com.aeasycredit.deployplugin;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.Executor;
import org.apache.commons.exec.LogOutputStream;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;

import com.aeasycredit.deployplugin.utils.ExecuteResult;
import com.aeasycredit.deployplugin.utils.FileHandlerUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

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
public class DeployPluginHelper {
    public final static String PLUGIN_ID = "com.aeasycredit.DeployPlugin";
    private final static String DEPLOY_PLUGIN = "DeployPlugin";
    
//    public static void success(Shell shell, String context){
//        MessageDialog.openInformation(shell, SLICE_PLUGIN, context);
//    }
//    
//    public static void error(Shell shell, String name, String context){
//        MessageDialog.openError(shell, name, context);
//    }

    private static MessageConsoleStream createConsole(String consoleName, boolean clean) {
        MessageConsole console = findConsole(consoleName);
        MessageConsoleStream cs = console.newMessageStream();
        cs.setEncoding("utf-8");
        cs.setColor(Display.getDefault().getSystemColor(SWT.COLOR_BLACK));
        if(clean){
            console.clearConsole();
        }
        console.activate();
        return cs;
    }

    private static MessageConsole findConsole(String name) {
        ConsolePlugin plugin = ConsolePlugin.getDefault();
        IConsoleManager conMan = plugin.getConsoleManager();
        IConsole[] existing = conMan.getConsoles();
        for (int i = 0; i < existing.length; i++) {
            if (name.equals(existing[i].getName())) {
                return (MessageConsole) existing[i];
            }
        }
        // no console found -> create new one
        MessageConsole newConsole = new MessageConsole(name, null);
        conMan.addConsoles(new IConsole[]{ newConsole });
        return newConsole;
    }
    


    public static MessageConsole findConsole() {
        return findConsole(DEPLOY_PLUGIN);
    }
    
    public static MessageConsoleStream console(boolean clean){
        MessageConsoleStream console = createConsole(DEPLOY_PLUGIN, clean);
        return console;
    }
    

    public static ExecuteResult exec(String workHome, String command, List<String> params, boolean isBatchScript) throws InterruptedException, IOException {
        return exec(null, workHome, command, params, isBatchScript);
    }
    
    public static ExecuteResult exec(final MessageConsoleStream console, String workHome, String command, List<String> parameters, boolean isBatchScript) throws IOException, InterruptedException {
    	boolean debug = DeployPluginLauncherPlugin.getGitBashDebug();
    	return exec(debug, console, workHome, command, parameters, isBatchScript);
    }

    /** 
             *   只支持sh脚本执行，如果是具体的命令的话，请使用CmdExecutor类执行
     *
     * @param console
     * @param workHome
     * @param command
     * @param params
     * @return
     * @throws IOException
     * @throws InterruptedException
     * 
     * @return boolean [返回类型说明]
     * @throws [异常类型] [异常说明]
     * @see [类、类#方法、类#成员]
     * @version [版本号, 2018年5月3日]
     * @author Dave.zhao
     */
    public static ExecuteResult exec(boolean debug, final MessageConsoleStream console, String workHome, String command, List<String> parameters, boolean isBatchScript) throws IOException, InterruptedException {
    	String debugStr = debug?"-x":"";
        
        CommandLine cmdLine = null;
        if(SystemUtils.IS_OS_WINDOWS) {
        	// For windows
        	cmdLine = new CommandLine(FileHandlerUtils.getGitHome()+"\\bin\\bash.exe");
            if(isBatchScript) {
            	// Batch script
                if(StringUtils.isNotBlank(debugStr)) {
                    cmdLine.addArgument(debugStr);
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
            	
            	// Supported using pipe in commands: can't contain "quotation mark"(双引号) in pipe 
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
        	if(isBatchScript) {
            	// Batch script
                if(StringUtils.isNotBlank(debugStr)) {
                    cmdLine.addArgument(debugStr);
                }
                cmdLine.addArgument(command);
                if(parameters!=null && !parameters.isEmpty()) {
                	for(String p : parameters) {
                		if(StringUtils.isNotBlank(p)) {
                    		cmdLine.addArgument(p);
                		}
                	}
                }
        	} else {
            	// single script
//        		cmdLine = new CommandLine(command);
//                if(parameters!=null && !parameters.isEmpty()) {
//                	for(String p : parameters) {
//        			if(StringUtils.isNotBlank(p)) {
//        				cmdLine.addArgument(p);
//        			}
//                }
        		
        		// Supported using pipe in commands: can't contain "quotation mark"(双引号) in pipe
        		String myActualCommand = command;
        		if(parameters!=null && !parameters.isEmpty()) {
            		String params = Joiner.on(" ").join(parameters);
        			myActualCommand += " "+params;
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
        if(console!=null) {
            executor.setStreamHandler(new PumpStreamHandler(new LogOutputStream() {

                @Override
                protected void processLine(String line, int level) {
                    console.println(line);
                }
            }, new LogOutputStream() {

                @Override
                protected void processLine(String line, int level) {
                    console.println(line);
                }
            }));
            
//            int code = executor.execute(cmdLine);
            try {
            	int code = executor.execute(cmdLine);
                executeResult = new ExecuteResult(code, ""+code);
            }catch(ExecuteException e) {
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
                executeResult = new ExecuteResult(code, outputStream.toString("utf-8"));
            }catch(ExecuteException e) {
                executeResult = new ExecuteResult(-1, e.getMessage());
            }
        }
        return executeResult;
    }
    
    public static void main(String[] args) throws Exception {
    	/*Process process = Runtime.getRuntime().exec(new String[]{"C:\\Program Files\\Git\\bin\\bash.exe","-c","whoami|grep dave"}); 
    	StringBuffer cmdout = new StringBuffer(); 
    	InputStream fis = process.getInputStream(); 
        BufferedReader br = new BufferedReader(new InputStreamReader(fis)); 
        String line = null; 
        while ((line = br.readLine()) != null) { 
            cmdout.append(line).append(System.getProperty("line.separator")); 
        } 
        System.out.println(cmdout);*/
        
        /*String myActualCommand = "whoami|grep dave"; 
    	// able to execute arbitrary shell command sequence 
    	CommandLine shellCommand = new CommandLine("bash").addArgument("-c"); 

    	// set handleQuoting = false so our command is taken as it is 
    	shellCommand.addArgument(myActualCommand, false); 

    	Executor exec = new DefaultExecutor(); 
    	// ... (configure the executor as you like, e.g. with watchdog and stream handler) 

    	ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
        PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream,errorStream);
        exec.setStreamHandler(streamHandler);
    	exec.execute(shellCommand); 
    	String out = outputStream.toString("utf-8");
        System.out.println(out);*/
    	
    	String workHome = "/Developer/workspace/config-server";
    	
    	String command = "git remote show origin|grep 1.18.2.hotfix | egrep '本地已过时|local out of date'";
    	List<String> params = Lists.newArrayList("");
        ExecuteResult executeResult = DeployPluginHelper.exec(true, null, workHome, command, params, false);
    	

//    	String command = "test.sh";
//    	List<String> params = Lists.newArrayList("");
//    	String output = DeployPluginHelper.exec(true, null, workHome, command, params, true);
    	
    	System.out.println(executeResult.getCode());
    	System.out.println(executeResult.getResult());
    }
}
