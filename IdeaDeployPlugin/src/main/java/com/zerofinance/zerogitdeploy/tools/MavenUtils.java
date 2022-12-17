package com.zerofinance.zerogitdeploy.tools;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.io.FileUtil;
import com.zerofinance.zerogitdeploy.handler.DependenciesDialogWrapper;
import com.zerofinance.zerogitdeploy.handler.MavenDependency;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.NameFileFilter;
import org.apache.commons.lang.StringUtils;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/**
 * <p>
 * <a href="MavenUtils.java"><i>View Source</i></a>
 *
 * @author Dave.zhao
 * Date: 12/17/2022 2:19 PM
 */
public final class MavenUtils {

    private MavenUtils(){}

    public static String getLatestVersion(String pattern, File realPrjParentPath) throws IOException {
        String latestVersion = "";
        String pomFile = realPrjParentPath+File.separator+"pom.xml";
        if(new File(pomFile).exists()) {
            System.out.println("pomFile--->"+pomFile);
            List<String> mvnString = FileUtils.readLines(new File(pomFile), StandardCharsets.UTF_8);
            for(String mvn: mvnString) {
                if(mvn.indexOf("<version>") != -1) {
                    latestVersion = mvn.replace("<version>","").replace("</version>", "");
                    break;
                }
            }
        }
        if(StringUtils.isNotBlank(latestVersion)) {
            latestVersion = latestVersion.trim();
        }
        return latestVersion;


//        Collection<File> folders = FileUtil.findFilesOrDirsByMask(Pattern.compile(pattern), realPrjParentPath);//.listFiles(new File(realPrjParentPath), FileFilterUtils.falseFileFilter(), FileFilterUtils.nameFileFilter(realPrj));
//        if(folders == null || folders.isEmpty()) {
//            folders = new ArrayList<>();
//            folders.add(realPrjParentPath);
//        }
//        String latestVersion = "";
//        for(File folder : folders) {
//            if(folder.isDirectory()) {
//                String pomFile = folder+File.separator+"pom.xml";
//                if(new File(pomFile).exists()) {
//                    System.out.println("pomFile--->"+pomFile);
//                    List<String> mvnString = FileUtils.readLines(new File(pomFile), StandardCharsets.UTF_8);
//                    for(String mvn: mvnString) {
//                        if(mvn.indexOf("<version>") != -1) {
//                            latestVersion = mvn.replace("<version>","").replace("</version>", "");
//                            break;
//                        }
//                    }
//                }
//            }
//            if(StringUtils.isNotBlank(latestVersion)) {
//                latestVersion = latestVersion.trim();
//                break;
//            }
//        }
//        return latestVersion;
    }

    public static MavenDependency getDependencies(String rootProjectPath) throws IOException {
        NameFileFilter nameFileFilter = new NameFileFilter("pom.xml");
        Collection<File> files = FileUtils.listFiles(new File(rootProjectPath), nameFileFilter, DirectoryFileFilter.DIRECTORY);
        Map<String, String> map = new LinkedHashMap<>();
        File pomFile = null;
        if(files != null) {
            for (File file : files) {
                pomFile = file;
                System.out.println("pomFile--->"+pomFile);
                String pomString = FileUtils.readFileToString(pomFile);
                String starTag = "Dependencies Properties Start";
                String endTag = "Dependencies Properties End";
                String dependencies = StringUtils.substringBetween(pomString, starTag, endTag);
                if(StringUtils.isNotBlank(dependencies)) {
                    String dependencieProperties = StringUtils.substringBetween(dependencies, "-->", "<!--").trim();
                    String[] properties = dependencieProperties.split("[\r\n]");
                    for(String property : properties) {
                        if(org.apache.commons.lang3.StringUtils.isNotBlank(property)) {
                            String prjAndVersion = property.trim();
                            String prj = org.apache.commons.lang3.StringUtils.substringBetween(prjAndVersion, "<", ">");//.replace("-version", "");
                            String version = org.apache.commons.lang3.StringUtils.substringBetween(prjAndVersion, ">", "</");
                            String realPrj = prj.replaceAll("[._-]version","");
                            String realPrjParentPath = new File(rootProjectPath).getParent()+File.separator+realPrj.replaceAll("[._-]api$", "");
                            String latestVersion = MavenUtils.getLatestVersion(realPrj, new File(realPrjParentPath));
                            map.put(prj, version+"/"+latestVersion);
                            System.out.println("prjAndVersion--->"+prjAndVersion);
                            System.out.println("map--->"+map);
                            System.out.println("prj--->"+prj);
                            System.out.println("realPrj--->"+realPrj);
                            System.out.println("version--->"+version);
                            System.out.println("realPrjParentPath--->"+realPrjParentPath);
                            System.out.println("latestVersion--->"+latestVersion);
                        }
                    }
                    // 只支持一个pom中的依赖替换逻辑
                    break;
                }
            }
        }
        MavenDependency mavenDependency = new MavenDependency();
        mavenDependency.setPomFile(pomFile);
        mavenDependency.setDependencies(map);
        return mavenDependency;
    }
}
