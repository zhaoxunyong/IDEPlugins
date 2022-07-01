package com.zerofinance.zerogitdeploy.tools;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import com.zerofinance.zerogitdeploy.setting.ZeroGitDeploySetting;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.SystemUtils;

public final class CommandUtils {
    
	private CommandUtils() {}
    
	public static String getRootProjectPath(String projectPath) {
//        String projectPath = project.getLocation().toFile().getPath();
        if (!new File(projectPath + File.separator + ".git").exists()) {
        	String parent = new File(projectPath).getParent();
        	return getRootProjectPath(parent);
        }
        if(SystemUtils.IS_OS_WINDOWS) {
        	projectPath = projectPath.replace("\\", "/");
        }
        return projectPath;
    }
    
    public static String processScript(String projectPath, String scriptName) throws Exception {
    	String tempFolder = getTempFolder();
//        String projectPath = project.getLocation().toFile().getPath();
        String rootProjectPath = getRootProjectPath(projectPath);
        String cmdFile = getCmdFile(projectPath, scriptName);
        String changVersionName = FilenameUtils.getName(cmdFile);
//        if(SystemUtils.IS_OS_WINDOWS) {
//            rootProjectPath = rootProjectPath.replace("\\", "\\\\");
//        }
//        InputStream input = this.getClass().getResourceAsStream("/merge.sh");
//        String str = IOUtils.toString(input);
        File scriptFile = new File(rootProjectPath+File.separator+changVersionName);
        String str = "";
        if(scriptFile.exists()) {
            str = FileUtils.readFileToString(scriptFile, "UTF-8");
        } else {
        	URL uri = new URL(getRootUrl()+"/"+changVersionName.replace("./", "/"));
        	InputStream input = uri.openStream();
        	try {
        		str = IOUtils.toString(input);
        	} finally {
        		IOUtils.closeQuietly(input);
        	}
        }
        String script = str;//.replace("#cd #{project}", "cd "+rootProjectPath);
        File file = new File(tempFolder+File.separator+changVersionName);
        FileUtils.writeStringToFile(file, script);
        if(SystemUtils.IS_OS_WINDOWS) {
            return file.getPath().replace("\\", "/");
        }
        return file.getPath();
    }
    
    private static String getRootUrl() {
        return ZeroGitDeploySetting.getScriptURL();
    }

    private static String getParentCmdFile(String projectPath, String cmd) {
        String rootProjectPath = getRootProjectPath(projectPath);
        return rootProjectPath+File.separator+cmd.replace("./", "");
    }
    
    public static String getTempFolder() {
//        if(SystemUtils.IS_OS_WINDOWS) {
//            return System.getenv("TEMP");   
//        } else {
//            String tempFolder =File.
//            if(!new File(tempFolder).exists()) {
//                new File(tempFolder).mkdirs();
//            }
//            return tempFolder;
//        }
    	File file = new File(System.getProperty("java.io.tmpdir"));
    	if(!file.exists()) {
    		file.mkdirs();
    	}
    	return file.getPath();
    }
    
//    public static String getGitHome() {
//        return GitDeployPluginSetting.getGitHome();
//    }
//
//    public static boolean isDebug() {
//        return GitDeployPluginSetting.isDebug();
//    }
//
//    public static boolean isMoreDetails() {
//        return GitDeployPluginSetting.isMoreDetails();
//    }

    private static String getCmdFile(String projectPath, String cmd) throws IOException {
        String allCmd = cmd.replace(".sh", "All.sh");
        String cmdFile = getParentCmdFile(projectPath, allCmd);
        if (new File(cmdFile).exists()) {
            return cmdFile;
        }
        return getParentCmdFile(projectPath, cmd);
    }

}
