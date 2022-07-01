package com.aeasycredit.deployplugin.handlers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.lang.StringUtils;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.internal.resources.Project;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.internal.core.CompilationUnit;
import org.eclipse.jdt.internal.core.JavaProject;
import org.eclipse.jdt.internal.core.PackageFragment;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;
import org.eclipse.ui.handlers.HandlerUtil;

import com.aeasycredit.deployplugin.CmdBuilder;
import com.aeasycredit.deployplugin.DeployPluginHelper;
import com.aeasycredit.deployplugin.DeployPluginLauncherPlugin;
import com.aeasycredit.deployplugin.dialogs.PasswordDialog;
import com.aeasycredit.deployplugin.exception.DeployPluginException;
import com.aeasycredit.deployplugin.jobs.ClientJob;
import com.aeasycredit.deployplugin.jobs.CompletionAction;
import com.aeasycredit.deployplugin.jobs.ListenerHandler;
import com.aeasycredit.deployplugin.jobs.ListenerJob;
import com.aeasycredit.deployplugin.jobs.Refreshable;
import com.aeasycredit.deployplugin.utils.BASE64Utils;
import com.aeasycredit.deployplugin.utils.ExecuteResult;
import com.aeasycredit.deployplugin.utils.FileHandlerUtils;
import com.google.common.collect.Lists;

/**
 * AbstractDeployPluginHandler
 * getCurrentRemoteBranch: git ls-remote | grep -v "\^{}" |  grep 'refs/heads' |awk '{print $NF}' | sed 's;refs/heads/;;g' | sort -t '.' -r -k 2 -V|egrep -i "\.x$" | sed -n '1p'
 * getMaxRemoteReleaseBranch: git ls-remote | grep -v "\^{}" |  grep 'refs/heads' |awk '{print $NF}' | sed 's;refs/heads/;;g' | sort -t '.' -r -k 2 -V|egrep -i "(release|hotfix)$" | sed -n '1p'

 * 
 * <p>
 * <a href="AbstractDeployPluginHandler.java"><i>View Source</i></a>
 * </p>
 * 
 * @author zhaoxunyong
 * @version 3.0
 * @since 1.0
 */
@SuppressWarnings("restriction")
public abstract class AbstractDeployPluginHandler extends AbstractHandler implements Refreshable {
    protected Shell shell;
    protected ISelection selection;

    private IProject project;
    private List<CompilationUnit> ifiles;
    protected MessageConsoleStream console;
    
    protected final static String CHANGEVERSION_BAT = "./changeVersion.sh";
    protected final static String RELEASE_BAT = "./release.sh";
    protected final static String NEWBRANCH_BAT = "./newBranch.sh";
    protected final static String TAG_BAT = "./tag.sh";
    protected final static String GITCHECK_BAT = "./gitCheck.sh";
    protected final static String COMMITED_LOGS_BAT = "./committedLogs.sh";
    
    @Deprecated
    protected final static String MERGE_BAT = "./merge.sh";
    protected final static String MYBATISGEN_BAT = "./mybatisGen.sh";
	private static File GIT_CACHE_FILE = new File(FileHandlerUtils.getTempFolder() + File.separator + "deployPluginCache");
    
    /**
     * 
     */
    public AbstractDeployPluginHandler() {
        this.console = DeployPluginHelper.console(true);
    }
    
//    public IProject getProject() {
//        return project;
//    }

    @SuppressWarnings("rawtypes")
    protected void init() throws Exception {
    	clearConsole();
        this.ifiles = Lists.newArrayList();
//      String name = "windows start";
        IProject project = null;
        if (selection.isEmpty() || selection instanceof TextSelection) {
//              MessageDialog.openError(shell, name, "No project or package selected.");
            throw new Exception("No project or package selected.");
        } else {
            if (selection instanceof TreeSelection) {
                TreeSelection ts = (TreeSelection) selection;
                if (!ts.isEmpty()) {
	        	    List<Object> itObjs = ts.toList();
	        	    if(itObjs.size() > 0) {
	        	    	for(Object itObj : itObjs) {
	        	    		if (itObj instanceof CompilationUnit) {
	        	    			CompilationUnit compilationUnit = (CompilationUnit) itObj;
			                	ifiles.add(compilationUnit);
			                	project = compilationUnit.getJavaProject().getProject();
			                } else {
			                	if (itObj instanceof Project) {
				                    Project prj = (Project) itObj;
				                    project = prj.getProject();
//				                    break;
				                } else if (itObj instanceof JavaProject) {
				                    JavaProject jproject = (JavaProject) itObj;
				                    project = jproject.getProject();
//				                    break;
				                } else if (itObj instanceof PackageFragment) {
				                    PackageFragment packageFragment = (PackageFragment) itObj;
				                    IJavaProject jproject = packageFragment.getJavaProject();
				                    project = jproject.getProject();
//				                    break;
				                }
			                }
	        	    	}
	        	    }

                }
            }
        }
        if (project == null) {
            throw new Exception("No project or package selected.");
        }
        this.project = project;
    }
    
    protected boolean preCheck() throws Exception {
    	boolean isConfirm = true;
        ExecuteResult result = null;
        try {
            result = this.gitCheck();
        } catch (Exception e) {
        	// Skipping check when the gitCheck.sh isn't existing
        	System.err.println(e);
        }
        if(result != null && result.getCode() != 0) {
//        	MessageDialog.openError(shell, "Local Out Of Date", result.getResult());
        	throw new Exception(result.getResult());
        } else {
        	try {
        		result = this.committedLogWarn();
        	} catch (Exception e) {
        		System.err.println(e);
        	}
        	if(result != null) {
//            	MessageDialog.openError(shell, "Local Out Of Date", result.getResult());
//            	throw new Exception(result.getResult());
        		isConfirm = MessageDialog.openConfirm(shell, "Committed Log Confirm?", "Did you forget merging some modified code?\n\n"+result.getResult());
            }
        }
        return isConfirm;
    }
    
    /*private void credentialHelper() throws Exception {
        String projectPath = project.getLocation().toFile().getPath();
//        String tempFile = getTempFolder();
//        File file = new File(tempFile+File.separator+UUID.randomUUID().toString());
//        FileUtils.writeStringToFile(file, data);
        try {
            DeployPluginHelper.exec(projectPath, "git ls-remote", "", true, true);
        } catch(Exception e) {
//            MessageDialog.openError(shell, "Credential Error", "Authentication error, please execute the following command through git bash:\n\ngit config --global credential.helper store\ngit ls-remote");
            throw new Exception("Authentication error, please execute the following command through git bash:\n\ngit config --global credential.helper store\ngit ls-remote");
        }
    }*/

    /*
     * (non-Javadoc)
     * 
     * @see com.chinatime.sliceplugin.popup.Refreshable#refresh()
     */
    @Override
    public void refresh() throws DeployPluginException {
        if (project == null) {
            throw new DeployPluginException("project is null.");
        }
        try {
            project.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
        } catch (CoreException e) {
            throw new DeployPluginException(e.getMessage(), e);
        }
    }

    private void clearConsole() {
        MessageConsole cs = DeployPluginHelper.findConsole();
        cs.clearConsole();
        cs.activate();
    }

    protected void changeVersion(ExecutionEvent event) throws Exception {
        changeVersion(event, "Change version");
    }

    protected void newBranch(ExecutionEvent event) throws Exception {
//        credentialHelper();
        newBranch(event, "New Branch");
    }

    protected void release(ExecutionEvent event) throws Exception {
//        credentialHelper();
        release(event, "Release");
    }

    protected void mybatisGen(ExecutionEvent event) throws Exception {
        mybatisGen(event, "Mybatis Gen");
    }
    
    /*private String selectJavaFolder() throws Exception {
    	DirectoryDialog javaDialog =
    		    new DirectoryDialog(shell, SWT.ICON_QUESTION | SWT.OK| SWT.CANCEL);
    	javaDialog.setText("Select A Java Target Folder");
    	javaDialog.setMessage("Please Select A Java Target Folder");

		// open dialog and await user selection
		final String javaFilePath = javaDialog.open();
		if(StringUtils.isBlank(javaFilePath)) {
    		throw new Exception("Please select a java folder first!");
//			MessageDialog.openWarning(shell, "Warning", "Please select a java folder first!");
//			return selectJavaFolder();
		}
        // /Developer/workspace/business-commons/business-organization/src/main/java
        // /Developer/workspace/saas/zero-react-admin/src/pages
		if(!javaFilePath.endsWith("src/main/java")) {
//    		throw new Exception("The javaFilePath must be located src/main/java folder!");
			MessageDialog.openWarning(shell, "Warning", "The javaFilePath must be located src/main/java folder!");
			return selectJavaFolder();
		}
		return javaFilePath;
    }*/
    
//    private String selectJsFolder() throws Exception {
//    	DirectoryDialog jsDialog =
//			    new DirectoryDialog(shell, SWT.ICON_QUESTION | SWT.OK| SWT.CANCEL);
//		jsDialog.setText("Select A JS Target Folder");
//		jsDialog.setMessage("Please Select A JS Target Folder");
//	
//		// open dialog and await user selection
//		final String jsFilePath = jsDialog.open();
//		if(StringUtils.isBlank(jsFilePath)) {
//    		throw new Exception("Please select a js folder first!");
////			MessageDialog.openWarning(shell, "Warning", "Please select a js folder first!");
////			return selectJsFolder();
//		}
//        // /Developer/workspace/saas/zero-react-admin/src/pages
//		if(!jsFilePath.endsWith("src/pages")) {
////    		throw new Exception("The jsFilePath must be located pages folder!");
//			MessageDialog.openWarning(shell, "Warning", "The jsFilePath must be located pages folder!");
//			return selectJsFolder();
//		}
//		return jsFilePath;
//    }

    /*protected void codeGen(ExecutionEvent event) throws Exception {
    	if(this.ifiles.isEmpty()) {
    		throw new Exception("Please select a java file first!");
    	}
    	String projectPath = project.getLocation().toFile().getPath();
    	final String javaFilePath = projectPath+File.separator+"src"+File.separator+"main"+File.separator+"java";
		String jsFilePath_ = DeployPluginLauncherPlugin.getCodeGenJsPath();
		if(StringUtils.isBlank(jsFilePath_)) {
			jsFilePath_ = this.selectJsFolder();
		}
		final String jsFilePath = jsFilePath_;
		final List<CompilationUnit> jobFiles = this.ifiles;
		final MessageConsoleStream finalConsole = this.console;
		boolean isConfirm = MessageDialog.openConfirm(shell, "Code Gen Confirm?", "Are you sure you want to generate the codes automatically?");
		if(isConfirm) {
	            final CompletionAction completionAction = new CompletionAction(DeployPluginHelper.PLUGIN_ID, shell, this, console);
	            completionAction.setOkTitle("Code generate successfully");
	            completionAction.setOkMsg("Code generate successfully");
	            completionAction.setFailTitle("Code generate failed");
	            completionAction.setFailMsg("There was an exception while executing CodeGen");
	            final ListenerJob job = new ListenerJob("DeployPlugin: CodeGen", completionAction, new ListenerHandler() {
	            	// https://stackoverflow.com/questions/5762491/how-to-print-color-in-console-using-system-out-println
	    			@Override
	    			public void process() throws Exception {
	    		    	for(CompilationUnit ifile : jobFiles) {
	    		        	final String className = ifile.getParent().getElementName()+"."+ifile.getElementName().replace(".java", "");
	//	    				finalConsole.println("Starting generate "+className+"...");
		    				String baseUrl = DeployPluginLauncherPlugin.getCodeGenUrl();
		    				String url = baseUrl +"?className="+className+"&javaFilePath="+URLEncoder.encode(javaFilePath, "utf-8")+"&jsFilePath="+URLEncoder.encode(jsFilePath, "utf-8");
		    				URL uri = new URL(url);
		    	        	InputStream input = uri.openStream();
		    	        	try {
		    	        		String output = IOUtils.toString(input);
	//	    	        		System.out.println("output------>"+output);
	//	    	        		Device device = Display.getDefault();
	//	    	        		RGB red = new RGB(255, 0, 0);
	//	    	        		Color color = new Color(device, red);
	//	    	        		finalConsole.setColor(color);
		    	        	    finalConsole.println(output);
	//	        				finalConsole.println(className+" has been generated successfully!");
		    	        	} finally {
		    	        		IOUtils.closeQuietly(input);
		    	        	}
		    			}
	    	    	}
	            	
	            });
	
	            // if short action, otherwise is long Job.LONG
	            job.setPriority(Job.SHORT);
	            // show a dialog immediately
	            job.setUser(false);
	            // start as soon as possible
	            job.schedule();
		}
    }*/
    
    /**
     * http://www.vogella.com/tutorials/EclipseDialogs/article.html
     */
    private String input(ExecutionEvent event, String name, String defaultValue, String example) throws Exception {
        InputDialog dlg = new InputDialog(
                HandlerUtil.getActiveShellChecked(event), name,
                "Enter parameter, example: " + example, defaultValue, null);
        String input = "";
        if (dlg.open() == Window.OK) {
            // User clicked OK
            input = dlg.getValue().trim();
            if(StringUtils.isBlank(input)) {
                throw new DeployPluginException("Parameter must not be empty.");
            }
        }
        return input;
    }
    
    private String desc(ExecutionEvent event, String name) throws Exception {
    	InputDialog dlg = new InputDialog(HandlerUtil.getActiveShellChecked(event), name, "Add a message for git description", "", null);
		String desc = "";
		if (dlg.open() == Window.OK) {
			// User clicked OK
			desc = dlg.getValue().trim();
			if (StringUtils.isBlank(desc)) {
//				throw new DeployPluginException("Please add a message for git description.");
				return desc(event, name);
			}
		}
		return desc;
    }
    
    /**
     * 濡傛灉version1>version2杩斿洖1锛屽鏋渧ersion<version2杩斿洖-1锛屽惁鍒欒繑鍥�0
     */
    private int compareVersion(String version1, String version2) {
    	if(version1==null || version2==null)
    		return 0;
    	
    	String[] str1 = version1.split("\\.");
    	String[] str2 = version2.split("\\.");
    	
    	for(int i=0; i<str1.length || i<str2.length;){
    		int n1 = i<str1.length ? Integer.parseInt(str1[i]) : 0;
    		int n2 = i<str2.length ? Integer.parseInt(str2[i]) : 0;
    		if(n1 > n2) return 1;
    		else if(n1 < n2) return -1;
    		else i++;			
    	}
    	
    	return 0;
    }
    
    /*private boolean gitAdd() throws Exception {
        String projectPath = project.getLocation().toFile().getPath();
        String rootProjectPath = FileHandlerUtils.getRootProjectPath(projectPath);
    	String command = "git status|grep 'git add'";
        List<String> parameters = Lists.newArrayList();
//    	String command = "git ls-remote | grep -v '\\^{}' |  grep 'refs/heads' |awk '{print $NF}' | sed 's;refs/heads/;;g' | sort -t '.' -r -k 2 -V|egrep -i '(release|hotfix)$'";
//      List<String> parameters = Lists.newArrayList();
        ExecuteResult executeResult = DeployPluginHelper.exec(rootProjectPath, command, parameters, false);
        int code = executeResult.getCode();
        return code != 0;
    }*/
    
    private ExecuteResult gitCheck() throws Exception {
        /*String projectPath = project.getLocation().toFile().getPath();
        String rootProjectPath = FileHandlerUtils.getRootProjectPath(projectPath);
    	String command = "git remote show origin|grep `git branch|grep '*' | awk '{print $NF}'` | egrep '鏈湴宸茶繃鏃秥local out of date'";
        List<String> parameters = Lists.newArrayList();
//    	String command = "git ls-remote | grep -v '\\^{}' |  grep 'refs/heads' |awk '{print $NF}' | sed 's;refs/heads/;;g' | sort -t '.' -r -k 2 -V|egrep -i '(release|hotfix)$'";
//      List<String> parameters = Lists.newArrayList();
        ExecuteResult executeResult = DeployPluginHelper.exec(rootProjectPath, command, parameters, false);
        int code = executeResult.getCode();
        return code != 0;*/
    	String projectPath = project.getLocation().toFile().getPath();
        String rootProjectPath = FileHandlerUtils.getRootProjectPath(projectPath);
    	String cmdFile = FileHandlerUtils.processScript(rootProjectPath, GITCHECK_BAT);
//    	String projectName = project.getLocation().toFile().getName();
//    	String tempProjectFolder = FileHandlerUtils.getTempFolder()+"/"+projectName;
    	List<String> params = Lists.newArrayList();
    	ExecuteResult executeResult = DeployPluginHelper.exec(rootProjectPath, cmdFile, params, true);
        return executeResult;
    }
    
    private ExecuteResult committedLogWarn() throws Exception {
    	String projectPath = project.getLocation().toFile().getPath();
        String rootProjectPath = FileHandlerUtils.getRootProjectPath(projectPath);
    	String cmdFile = FileHandlerUtils.processScript(rootProjectPath, COMMITED_LOGS_BAT);
//    	String projectName = project.getLocation().toFile().getName();
//    	String tempProjectFolder = FileHandlerUtils.getTempFolder()+"/"+projectName;
    	List<String> params = Lists.newArrayList();
    	ExecuteResult executeResult = DeployPluginHelper.exec(rootProjectPath, cmdFile, params, true);
        return executeResult;
    }
    
    private String[] lsRemote(String rootProjectPath) throws Exception {
    	String command = "git";
        List<String> parameters = Lists.newArrayList("ls-remote");
//    	String command = "git ls-remote | grep -v '\\^{}' |  grep 'refs/heads' |awk '{print $NF}' | sed 's;refs/heads/;;g' | sort -t '.' -r -k 2 -V|egrep -i '(release|hotfix)$'";
//      List<String> parameters = Lists.newArrayList();
        ExecuteResult executeResult = DeployPluginHelper.exec(rootProjectPath, command, parameters, false);
        // String result = CmdExecutor.exec(rootProjectPath, command, parameters);
//	        System.out.println("result----->"+result);
        int code = executeResult.getCode();
        String result = executeResult.getResult();
        if(code == 0 && !"".equals(result)) {
        	return result.split("[\n|\r\n]");
        }
        return null;
    }
    
    private String getCurrentDevelopVersion(String[] results) throws Exception {
    	String currentBranch = "";
        if(results != null) {
		    String version2 = "0.0.0";
    		for(String version : results) {
    			if(version.endsWith(".x")) {
//		            String remoteBranchVersion = version.split("/")[2];
		            String remoteBranchVersion = StringUtils.substringAfterLast(version, "/");//.replace(".release", "").replace(".hotfix", "");
		            String version1 = remoteBranchVersion;
		            // 濡傛灉version1>version2杩斿洖1锛屽鏋渧ersion<version2杩斿洖-1锛屽惁鍒欒繑鍥�0
		            if(compareVersion(version1, version2) == 1) {
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
        if(results != null) {
		    String version2 = "0.0.0";
    		for(String version : results) {
    			if(version.endsWith(".release") || version.endsWith(".hotfix")) {
//		            String remoteBranchVersion = version.split("/")[2];
		            String remoteBranchVersion = StringUtils.substringAfterLast(version, "/");//.replace(".release", "").replace(".hotfix", "");
		            String version1 = remoteBranchVersion.replaceAll("\\.release|\\.hotfix", "");
		            // 濡傛灉version1>version2杩斿洖1锛屽鏋渧ersion<version2杩斿洖-1锛屽惁鍒欒繑鍥�0
		            if(compareVersion(version1, version2) == 1) {
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
        String pomFile = rootProjectPath+"/pom.xml";
        if(new File(pomFile).exists()) {
            List<String> value = FileUtils.readLines(new File(pomFile));
            for(String v : value) {
                if(v.indexOf("<version>")!=-1) {
                    version = StringUtils.substringBetween(v, "<version>", "</version>");
                    break;
                }
            }
        } else {
//        	throw new DeployPluginException("It seems not a maven project, if you're using non-maven project, please handle it by vscode plugin!");
        	String[] results = this.lsRemote(rootProjectPath);
        	String currentDevelopVersion = getCurrentDevelopVersion(results);
        	if(StringUtils.isBlank(currentDevelopVersion)) {
        		throw new Exception("Please create a branch first.");
        	}
        	String[] currentBranchs = currentDevelopVersion.split("[.]");
        	String b1 = currentBranchs[0];
        	String b2 = currentBranchs[1];
        	version = getRemoteVersion(results).replaceAll("\\.(release|hotfix)$", "");
        	if(StringUtils.isBlank(version)) {
        		// Not found the lastest release version, using the current branch instead
        		version = b1+"."+b2+".0";
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
        for(String v : value) {
            if(v.indexOf(var) !=-1 && v.indexOf("-SNAPSHOT") != -1) {
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
             for(String v : value) {
                 if(v.indexOf("<dependency>") !=-1) {
                     isNew = true;
                 } else if(v.indexOf("</dependency>") !=-1) {
                     isNew = false;
                 }
                 
                 if(isNew && v.indexOf("version")!=-1) {
                     // && v.indexOf("-SNAPSHOT") != -1
                     if(v.indexOf("${") != -1) {
                         String var = StringUtils.substringBetween(v, "${", "}");
                         String snapshotVar = haSnapshotVersion(value, var);
                         if(StringUtils.isNotBlank(snapshotVar)) {
                             return pomFile+"("+snapshotVar+")";
                         }
                     } else if(v.indexOf("-SNAPSHOT") != -1) {
                         return pomFile+"("+v.trim()+")";
                     }
                 }
             }    
         }
         return "";
    }

    private void changeVersion(ExecutionEvent event, String name) throws Exception {
        List<CmdBuilder> cmdBuilders = Lists.newLinkedList();
        String projectPath = project.getLocation().toFile().getPath();
        String cmdFile = FileHandlerUtils.processScript(projectPath, CHANGEVERSION_BAT);
        String rootProjectPath = FileHandlerUtils.getRootProjectPath(projectPath);
//        String cmdName = FilenameUtils.getName(cmdFile);
        String pomVersion = getMavenPomVersion(rootProjectPath);
        String bPomVersion = StringUtils.substringBeforeLast(pomVersion, ".");
        String aPomVersion = StringUtils.substringAfterLast(pomVersion, ".").replace("-SNAPSHOT", "");
        aPomVersion = String.valueOf(Integer.parseInt(aPomVersion)+1);
        String defaultValue = bPomVersion+"."+aPomVersion+"-SNAPSHOT";
        List<String> parameters = Lists.newArrayList();
        String params = input(event, name, defaultValue, "newVersion");
        if(StringUtils.isNotBlank(params)) {
            parameters.add(params);
            
            if(parameters!=null && !parameters.isEmpty()) {
//                String projectPath = project.getLocation().toFile().getPath();
//                String rootProjectPath = getParentProject(projectPath, cmd);
                
                cmdBuilders.add(new CmdBuilder(rootProjectPath, cmdFile, true, parameters));
                if (cmdBuilders != null && !cmdBuilders.isEmpty()) {
                    runJob(name, cmdBuilders);
                } else {
//                    MessageDialog.openError(shell, name, "No project or pakcage selected.");
                    throw new Exception("No project or package selected.");
                }
            }
        }
    }

    private void newBranch(ExecutionEvent event, String name) throws Exception {
        List<CmdBuilder> cmdBuilders = Lists.newLinkedList();
        String projectPath = project.getLocation().toFile().getPath();
        String cmdFile = FileHandlerUtils.processScript(projectPath, NEWBRANCH_BAT);
        String rootProjectPath = FileHandlerUtils.getRootProjectPath(projectPath);
//        String cmdName = FilenameUtils.getName(cmdFile);
        String pomVersion = getMavenPomVersion(rootProjectPath);
        // 1.5.6->1.6.x
        String bPomVersion = StringUtils.substringBeforeLast(pomVersion, "."); //1.5
        String a1PomVersion = StringUtils.substringBeforeLast(bPomVersion, "."); // 1
        String a2PomVersion = StringUtils.substringAfterLast(bPomVersion, "."); // 5
        a2PomVersion = String.valueOf(Integer.parseInt(a2PomVersion)+1);
        String defaultValue = a1PomVersion+"."+a2PomVersion+".x"; // 1.6.x
        List<String> parameters = Lists.newArrayList();
        String params = input(event, name, defaultValue, "newBranch");
        parameters.add(params);
        
        if(parameters!=null && !parameters.isEmpty()) {
//            String projectPath = project.getLocation().toFile().getPath();
//            String rootProjectPath = getParentProject(projectPath, cmd);
            String desc = desc(event, name);
            if(StringUtils.isNotBlank(desc)) {
//            	params = params + " '" + desc +"'";
            	parameters.add(desc);
                cmdBuilders.add(new CmdBuilder(rootProjectPath, cmdFile, true, parameters));
                if (cmdBuilders != null && !cmdBuilders.isEmpty()) {
                    runJob(name, cmdBuilders);
                } else {
//                    MessageDialog.openError(shell, name, "No project or pakcage selected.");
                    throw new Exception("No project or package selected.");
                }
            	
            }
        }
    }

    private void mybatisGen(ExecutionEvent event, String name) throws Exception {
        List<CmdBuilder> cmdBuilders = Lists.newLinkedList();

        String projectPath = project.getLocation().toFile().getPath();
//        String rootProjectPath = FileHandlerUtils.getRootProjectPath(projectPath);
//        String cmdFile = FileHandlerUtils.getCmdFile(projectPath, MYBATISGEN_BAT);
        String cmdFile = FileHandlerUtils.processScript(projectPath, MYBATISGEN_BAT);
//        String cmdName = FilenameUtils.getName(cmdFile);
        // Using "projectPath" instead of "rootProjectPath"
        cmdBuilders.add(new CmdBuilder(projectPath, cmdFile, true, Lists.newArrayList()));
        if (cmdBuilders != null && !cmdBuilders.isEmpty()) {
            boolean isConfirm = MessageDialog.openConfirm(shell, "Mybatis Gen Confirm?", project.getName() + " Mybatis Gen Confirm?");
            if(isConfirm) {
                runJob(name, cmdBuilders);
            }
        } else {
//            MessageDialog.openError(shell, name, "No project or pakcage selected.");
            throw new Exception("No project or package selected.");
        }
    }
    
    /**
     * Verify the account and password is correct?
     * 
     * @param user username
     * @param pwd password
     * @param rememberPwd Remember username and password?
     * @return
     */
    private boolean checkUserPwd(String user, String pwd, boolean rememberPwd) {
		String projectPath = project.getLocation().toFile().getPath();
		String rootProjectPath = FileHandlerUtils.getRootProjectPath(projectPath);
    	try {
			Git git = Git.open(new File(rootProjectPath));
			git.lsRemote().setCredentialsProvider(new UsernamePasswordCredentialsProvider(user, pwd)).call();
			// save cache
			if(rememberPwd) {
				if (!GIT_CACHE_FILE.getParentFile().exists()) {
					GIT_CACHE_FILE.getParentFile().mkdirs();
				}
				List<String> datas = new ArrayList<String>();
				datas.add(user);
				datas.add(BASE64Utils.encoder(pwd));
				datas.add(String.valueOf(rememberPwd));
				FileUtils.writeLines(GIT_CACHE_FILE, datas);
			}
			git.close();
			return true;
		} catch (Exception e) {
			return false;
		}
    }
    
	private UsernamePasswordCredentialsProvider getGitCache() throws IOException {
		System.out.println("gitCacheFile-------->" + GIT_CACHE_FILE);
		if (GIT_CACHE_FILE.exists()) {
			@SuppressWarnings("unchecked")
			List<String> datas = FileUtils.readLines(GIT_CACHE_FILE);
			String user = datas.get(0);
			String pwd = BASE64Utils.decoder(datas.get(1));
			boolean rememberPwd = Boolean.valueOf(datas.get(2));
			if(rememberPwd && checkUserPwd(user, pwd, true)) {
				return new UsernamePasswordCredentialsProvider(user, pwd);
			}
		}
		
		PasswordDialog dialog = new PasswordDialog(shell);
		// get the new values from the dialog
		if (dialog.open() == Window.OK) {
			String user = dialog.getUser();
			String pwd = dialog.getPassword();
			Boolean remember = dialog.getCheck();
			System.out.println("user--->" + user);
			System.out.println("pwd--->" + pwd);
			System.out.println("remember--->" + remember);
			if(checkUserPwd(user, pwd, remember)) {
				return new UsernamePasswordCredentialsProvider(user, pwd);
			} else {
				return getGitCache();
			}
		}
		return null;
	}
	
	private List<String> getReleaseList() throws Exception {
    	List<String> allReleases = Lists.newArrayList();
    	String projectPath = project.getLocation().toFile().getPath();
        String rootProjectPath = FileHandlerUtils.getRootProjectPath(projectPath);
    	
        // Get credentials provide via JGit
        
//		UsernamePasswordCredentialsProvider provider = getGitCache();
        String username = DeployPluginLauncherPlugin.getGitUsername();
        String password = DeployPluginLauncherPlugin.getGitPassword();
        if(StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password)) {
    		UsernamePasswordCredentialsProvider provider = new UsernamePasswordCredentialsProvider(username, password);
        	if(provider != null) {
            	Git git = Git.open(new File(rootProjectPath));
            	Collection<Ref> refs = git.lsRemote()
                		.setCredentialsProvider(provider)
                		.call();
                for (Ref ref : refs) {
                    String r = ref.getName();
//                    System.out.println("ref--->" + r);
            		if(r.endsWith(".release") || r.endsWith(".hotfix")) {
            			String version = StringUtils.substringAfterLast(r, "/");//.replace(".release", "").replace(".hotfix", "");
                        allReleases.add(version);
            		}
                }
                git.close();
        	}
        } else {
            String command = "git";
            List<String> parameters = Lists.newArrayList("ls-remote");
            ExecuteResult executeResult = DeployPluginHelper.exec(rootProjectPath, command, parameters, false);
            int code = executeResult.getCode();
            String result = executeResult.getResult();
            if(code == 0 && !"".equals(result)) {
            	String[] results = result.split("[\n|\r\n]");
            	for(String r : results) {
            		if(r.endsWith(".release") || r.endsWith(".hotfix")) {
            			String version = StringUtils.substringAfterLast(r, "/");//.replace(".release", "").replace(".hotfix", "");
                        allReleases.add(version);
            		}
            	}
            }
        }
        
        // Order by list
        /*Collections.sort(allReleases, new Comparator<String>() {
			@Override
			public int compare(String o1, String o2) {
				String a1 = o1.replace("release", "").replace("hotfix", "").replace(".", "");
				String a2 = o2.replace("release", "").replace("hotfix", "").replace(".", "");
				return Integer.parseInt(a2) - Integer.parseInt(a1);
			}
        });*/
    	return allReleases;
	}
    
    private void release(ExecutionEvent event, String name) throws Exception {
        List<CmdBuilder> cmdBuilders = Lists.newLinkedList();
        
        String projectPath = project.getLocation().toFile().getPath();
        String rootProjectPath = FileHandlerUtils.getRootProjectPath(projectPath);
        
        boolean continute = true;
        try {
	        String snapshotPath = checkHasSnapshotVersion(rootProjectPath);
	        if(StringUtils.isNotBlank(snapshotPath)) {
	            continute = MessageDialog.openConfirm(shell, "process confirm?", "There is a SNAPSHOT version in "+snapshotPath+", when the version is released, it's suggested to replace it as release version. Do you want to continue?");
	        }
        }catch(Exception e) {
        	// do nothing
        }
        
        if(continute) {
        	SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmm");
        	String dateString = formatter.format(new Date());
        	   
	        ElementListSelectionDialog releaseTypeDialog =
	                new ElementListSelectionDialog(shell, new LabelProvider());
	        List<String> releaseTypes = Lists.newArrayList("release", "hotfix");
	        if(DeployPluginLauncherPlugin.getGitShowTagInDropDown()) {
	        	releaseTypes.add("tag");
	        }
	        
	        releaseTypeDialog.setElements(releaseTypes.toArray(new String[releaseTypes.size()]));
	        releaseTypeDialog.setTitle("Which release type do you want to pick?");
            if (releaseTypeDialog.open() == Window.OK) {
                String releaseType = (String) releaseTypeDialog.getFirstResult();
//		        System.out.println("releaseType----->"+releaseType);
		        if("release".equals(releaseType) || "hotfix".equals(releaseType)) {
		        	// release or hotfix
		            
//		            String tempFolder = getTempFolder();
		            String cmdFile = FileHandlerUtils.processScript(rootProjectPath, RELEASE_BAT);
//		            String cmdName = FilenameUtils.getName(cmdFile);
		            
		            String pomVersion = getMavenPomVersion(rootProjectPath);
		            String defaultValue = pomVersion.replace("-SNAPSHOT", "")+"."+releaseType;
		            
		            String inputedVersion = input(event, name, defaultValue, "BranchVersion TagDate").trim();
		            if( inputedVersion.indexOf(" ") != -1) {
	                    throw new Exception("The version is invalid.");
		            }
		            
		            if(StringUtils.isNotBlank(inputedVersion)) {
//		                String projectPath = project.getLocation().toFile().getPath();
//		                String rootProjectPath = getParentProject(projectPath, cmd);
		            	boolean releaseWithTag = DeployPluginLauncherPlugin.getGitReleaseWithTag();
		            	boolean goahead = false;
		            	if(releaseWithTag) {
			            	MessageDialog dialog = new MessageDialog(
			            		      null, "Are you sure to tag?", null, "It will tag "+inputedVersion+"-"+dateString+" for "+inputedVersion+" automatically.",
			            		      MessageDialog.QUESTION,
			            		      new String[] {"Yes", "Cancel", "No"},
			            		      0); // yes is the default
		            		   int result = dialog.open();
		            		   if(result == 2) {
		            			   // "No" is selected
		            			   releaseWithTag = false;
		            		   }
		            		   // 0:Yes 1: Cancel 2:No
		            		   // excute if not canceled
		            		   if(result == 0 || result == 2) {
		            			   goahead = true;
		            		   }
		            	} else {
		            		goahead = true;
		            	}
		            	if(goahead) {
	   		                
							String desc = desc(event, name);
							if(StringUtils.isNotBlank(desc)) {
		   		                List<String> parameters = Lists.newArrayList(inputedVersion, dateString, releaseWithTag+"", "\""+desc+"\"");
		   		                cmdBuilders.add(new CmdBuilder(rootProjectPath, cmdFile, true, parameters));
		   		                if (cmdBuilders != null && !cmdBuilders.isEmpty()) {
		   		                    runJob(name, cmdBuilders);
		   		                } else {
//			   		                    MessageDialog.openError(shell, name, "No project or pakcage selected.");
		   		                    throw new Exception("No project or package selected.");
		   		                }
							}
		            	}
		            }
		        } else {
		        	List<String> allReleases = this.getReleaseList();
		        	if(!allReleases.isEmpty()) {
			        	ElementListSelectionDialog dlgs =
				                new ElementListSelectionDialog(shell, new LabelProvider());
			            dlgs.setElements(allReleases.toArray(new String[allReleases.size()]));
			            dlgs.setTitle("Which release version do you want to pick?");
			            if (dlgs.open() == Window.OK) {
			                String releaseVersion = (String) dlgs.getFirstResult();
			                String desc = desc(event, name);
			                if(StringUtils.isNotBlank(desc)) {
				                // String parameters = releaseVersion +" "+ dateString + " '"+desc+"'";
		   		                List<String> parameters = Lists.newArrayList(releaseVersion, dateString, desc);
//						        System.out.println("releaseVersion----->"+releaseVersion);
					            String cmdFile = FileHandlerUtils.processScript(rootProjectPath, TAG_BAT);
						        cmdBuilders.add(new CmdBuilder(rootProjectPath, cmdFile, true, parameters));
				                if (cmdBuilders != null && !cmdBuilders.isEmpty()) {
				                    runJob(name, cmdBuilders);
				                } else {
//				                    MessageDialog.openError(shell, name, "No project or pakcage selected.");
				                    throw new Exception("No project or package selected.");
				                }
			                }
			            }
		        	} else {
		        		MessageDialog.openError(shell, name, "Neither the release nor the hotfix version has been found!");
		        	}
		        	
		        }
            }
        	
        	
        }
    }

    protected void runJob(final String name, List<CmdBuilder> cmdBuilders) throws CoreException {
//        boolean isConfirm = MessageDialog.openConfirm(shell, "process confirm?", project.getName() + " process confirm?");

//        if (isConfirm) {

        // create action to be called after the action is completed
        // this action shows a success dialog if the job executed without
        // an exception, otherwise it shows an error dialog
        final CompletionAction completionAction = new CompletionAction(DeployPluginHelper.PLUGIN_ID, shell, this,
                console);
        completionAction.setOkTitle(name + " sucess");
        completionAction.setOkMsg("Sucessfully executed " + name);
        completionAction.setFailTitle(name + " failed");
        completionAction.setFailMsg("There was an exception while executing " + name);

        final ClientJob job = new ClientJob("DeployPlugin:" + name, cmdBuilders, completionAction);

        // if short action, otherwise is long Job.LONG
        job.setPriority(Job.SHORT);
        // show a dialog immediately
        job.setUser(false);
        // job.setSystem(true);
        // start as soon as possible
        job.schedule();
        
        /*String mode = ILaunchManager.RUN_MODE;
            if (debug)
                mode = ILaunchManager.DEBUG_MODE;
            else
                mode = ILaunchManager.RUN_MODE;
            ILaunchConfigurationWorkingCopy config = createConfig(name);
            
            final ILaunch ILAUNCH = config.launch(mode, null);
            
//          final ILaunch pendingLaunch = new DebugUIPlugin.PendingLaunch(createConfig(name), mode, job);
            DebugPlugin.getDefault().getLaunchManager().addLaunch(ILAUNCH);
            
            IJobChangeListener listener = new IJobChangeListener() {
                @Override
                public void sleeping(IJobChangeEvent event) {
                }

                @Override
                public void scheduled(IJobChangeEvent event) {
                }

                @Override
                public void running(IJobChangeEvent event) {
                }

                @Override
                public void awake(IJobChangeEvent event) {
                }

                @Override
                public void aboutToRun(IJobChangeEvent event) {
                    
                }

                @Override
                public void done(IJobChangeEvent event) {
                    DebugPlugin dp = DebugPlugin.getDefault();
                    if (dp != null) {
                        dp.getLaunchManager().removeLaunch(ILAUNCH);
                    }
                    job.removeJobChangeListener(this);
                }
            };
            job.addJobChangeListener(listener);*/
        
        // job.addJobChangeListener(new JobChangeAdapter() {
        // @Override
        // public void done(IJobChangeEvent event) {
        // if (event.getResult().isOK()) {
        // SlicePluginTools.success(shell, name+" completed successfully");
        // } else {
        // MessageDialog.openError(shell, name+" did not complete
        // successfully");
        // }
        // }
        // });
//        }
    }
}
