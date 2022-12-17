package com.zerofinance.zerogitdeploy.tools;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.NameFileFilter;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>
 * <a href="PomUtils.java"><i>View Source</i></a>
 *
 * @author Dave.zhao
 * Date: 12/16/2022 6:24 PM
 */
public final class PomUtils {

    public static void main(String[] args) throws Exception {
        File projectpath = new File("D:\\Developer\\idea_workspace\\X-Pay\\operation-server");
        NameFileFilter nameFileFilter = new NameFileFilter("pom.xml");
        Collection<File> files = FileUtils.listFiles(projectpath, nameFileFilter, DirectoryFileFilter.DIRECTORY);
        if(files != null) {
            for (File file : files) {
                System.out.println("file--->"+file);
                String pomString = FileUtils.readFileToString(file);
                String starTag = "Dependencies Properties Start";
                String endTag = "Dependencies Properties End";
                String dependencies = StringUtils.substringBetween(pomString, starTag, endTag);
                if(StringUtils.isNotBlank(dependencies)) {
                    String dependencieProperties = StringUtils.substringBetween(pomString, "-->", "<!--").trim();
                    String[] properties = dependencieProperties.split("[\r\n]");
                    Map<String, String> map = new HashMap<>();
                    for(String property : properties) {
                        if(StringUtils.isNotBlank(property)) {
                            String prjAndVersion = property.trim();
                            System.out.println("prjAndVersion--->"+prjAndVersion);
                            String prj = StringUtils.substringBetween(prjAndVersion, "<", ">").replace("-version", "");
                            String version = StringUtils.substringBetween(prjAndVersion, ">", "</");
                            map.put(prj, version);
                            System.out.println("prj--->"+prj);
                            System.out.println("version--->"+version);
                        }
                    }
                }
            }
        }
    }
}
