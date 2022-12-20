package com.zerofinance.zerogitdeploy.tools;

import com.zerofinance.zerogitdeploy.handler.MavenDependency;
import com.zerofinance.zerogitdeploy.setting.ZeroGitDeploySetting;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.NameFileFilter;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * <p>
 * <a href="MavenUtils.java"><i>View Source</i></a>
 *
 * @author Dave.zhao
 * Date: 12/17/2022 2:19 PM
 */
public final class MavenUtils {

    private MavenUtils(){}

    public static String getLatestVersion(String dependenciesProject) throws IOException {
        String latestVersion = "";
        String mavenRepoUrl = ZeroGitDeploySetting.getMavenRepoUrl();
        // http://nexus.zerofinance.net/service/local/lucene/search?q=merchant-server-api&collapseresults=true
        String versionUrl = mavenRepoUrl+"/service/local/lucene/search?q='"+dependenciesProject+"'&collapseresults=true";
        URL uri = new URL(versionUrl);
        InputStream input = uri.openStream();
        try {
            List<String> result = IOUtils.readLines(input, StandardCharsets.UTF_8);
            for(String r:result) {
                if(r.indexOf("<latestRelease>") != -1) {
                    latestVersion = r.replace("<latestRelease>","").replace("</latestRelease>","").trim();
                    break;
                }
            }
        } finally {
            IOUtils.closeQuietly(input);
        }
//        System.out.println("latestVersion--->"+latestVersion);
        return latestVersion;
    }

    public static List<MavenDependency> getDependencies(String rootProjectPath) throws IOException {
        NameFileFilter nameFileFilter = new NameFileFilter("pom.xml");
        Collection<File> files = FileUtils.listFiles(new File(rootProjectPath), nameFileFilter, DirectoryFileFilter.DIRECTORY);
        List<MavenDependency> mavenDependencies = new LinkedList<>();
        if(files != null) {
            for (File file : files) {
                File pomFile = file;
                String pomString = FileUtils.readFileToString(pomFile);
                String starTag = "Dependencies Properties Start";
                String endTag = "Dependencies Properties End";
                String dependencies = StringUtils.substringBetween(pomString, starTag, endTag);
                if(StringUtils.isNotBlank(dependencies)) {
                    System.out.println("pomFile--->"+pomFile);
                    String dependencieProperties = StringUtils.substringBetween(dependencies, "-->", "<!--").trim();
                    String[] properties = dependencieProperties.split("[\r\n]");
                    for(String property : properties) {
                        if(org.apache.commons.lang3.StringUtils.isNotBlank(property)) {
                            String prjAndVersion = property.trim();
                            String moduleName = org.apache.commons.lang3.StringUtils.substringBetween(prjAndVersion, "<", ">");//.replace("-version", "");
                            String version = org.apache.commons.lang3.StringUtils.substringBetween(prjAndVersion, ">", "</");
                            String realModuleName = moduleName.replaceAll("[._-]version","");
                            String realPrjParentPath = new File(rootProjectPath).getParent()+File.separator+realModuleName.replaceAll("[._-]api$", "");
                            String latestVersion = MavenUtils.getLatestVersion(realModuleName);

                            MavenDependency mavenDependency = new MavenDependency();
                            mavenDependency.setPomFile(pomFile);
                            mavenDependency.setCurrVersion(version);
                            mavenDependency.setLatestVersion(latestVersion);
                            mavenDependency.setModuleName(moduleName);
                            mavenDependencies.add(mavenDependency);

//                            System.out.println("prjAndVersion--->"+prjAndVersion);
//                            System.out.println("moduleName--->"+moduleName);
//                            System.out.println("realModuleName--->"+realModuleName);
//                            System.out.println("version--->"+version);
//                            System.out.println("realPrjParentPath--->"+realPrjParentPath);
//                            System.out.println("latestVersion--->"+latestVersion);
                        }
                    }
                }
            }
        }
        return mavenDependencies;
    }
}
