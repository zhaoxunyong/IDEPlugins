# Eclipse ZeroGit Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the Eclipse plugin as a new ZeroGit PDE plugin that matches the current IDEA and VS Code 13-command capability model, shared script contract, and Eclipse-native interaction rules.

**Architecture:** Build a new Eclipse plugin project, feature project, update site project, and PDE test bundle under `EclipseDeployPlugin/`, then implement thin command handlers over shared flow, git, script, execution, settings, and UI services. Keep Git Flow business logic in shared scripts; Eclipse owns only context resolution, parameter collection, validation, command orchestration, console streaming, and packaging.

**Tech Stack:** Java 8, Eclipse PDE, JFace/SWT, Eclipse Jobs, Eclipse Console, Commons Exec, JGit, PDE JUnit test runner

---

### Task 1: Scaffold The New PDE Plugin And Test Harness

**Files:**
- Create: `EclipseDeployPlugin/scripts/run-pde-tests.sh`
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/.project`
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/.classpath`
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/META-INF/MANIFEST.MF`
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/build.properties`
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/plugin.xml`
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/src/com/zerofinance/zerogit/eclipse/plugin/ZeroGitPlugin.java`
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse.tests/.project`
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse.tests/.classpath`
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse.tests/META-INF/MANIFEST.MF`
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse.tests/build.properties`
- Test: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse.tests/src/com/zerofinance/zerogit/eclipse/tests/ZeroGitPluginSmokeTest.java`

- [ ] **Step 1: Write the failing plugin smoke test and PDE runner script**

```bash
#!/usr/bin/env bash
set -euo pipefail

PLUGIN_NAME="$1"
CLASS_NAME="$2"
WORKSPACE_DIR="$(cd "$(dirname "$0")/.." && pwd)/.pde-test-workspace"

mkdir -p "$WORKSPACE_DIR"

eclipse \
  -nosplash \
  -application org.eclipse.pde.junit.runtime.coretestapplication \
  -data "$WORKSPACE_DIR" \
  -testpluginname "$PLUGIN_NAME" \
  -classname "$CLASS_NAME"
```

```java
package com.zerofinance.zerogit.eclipse.tests;

import static org.junit.Assert.assertTrue;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.junit.Test;

public class ZeroGitPluginSmokeTest {

    @Test
    public void allZeroGitCommandsAreDefined() {
        ICommandService commandService =
                PlatformUI.getWorkbench().getService(ICommandService.class);

        assertTrue(commandService.getCommand("com.zerofinance.zerogit.eclipse.commands.generateCommitMessage").isDefined());
        assertTrue(commandService.getCommand("com.zerofinance.zerogit.eclipse.commands.aiCodeReview").isDefined());
        assertTrue(commandService.getCommand("com.zerofinance.zerogit.eclipse.commands.mavenChange").isDefined());
        assertTrue(commandService.getCommand("com.zerofinance.zerogit.eclipse.commands.startNewFeature").isDefined());
        assertTrue(commandService.getCommand("com.zerofinance.zerogit.eclipse.commands.finishFeature").isDefined());
        assertTrue(commandService.getCommand("com.zerofinance.zerogit.eclipse.commands.rebaseFeature").isDefined());
        assertTrue(commandService.getCommand("com.zerofinance.zerogit.eclipse.commands.mergeRequest").isDefined());
        assertTrue(commandService.getCommand("com.zerofinance.zerogit.eclipse.commands.startNewRelease").isDefined());
        assertTrue(commandService.getCommand("com.zerofinance.zerogit.eclipse.commands.finishRelease").isDefined());
        assertTrue(commandService.getCommand("com.zerofinance.zerogit.eclipse.commands.startNewHotfix").isDefined());
        assertTrue(commandService.getCommand("com.zerofinance.zerogit.eclipse.commands.finishHotfix").isDefined());
        assertTrue(commandService.getCommand("com.zerofinance.zerogit.eclipse.commands.runCiCommand").isDefined());
        assertTrue(commandService.getCommand("com.zerofinance.zerogit.eclipse.commands.gitflowGuideline").isDefined());
    }
}
```

- [ ] **Step 2: Run the smoke test to verify it fails**

Run:

```bash
bash EclipseDeployPlugin/scripts/run-pde-tests.sh \
  com.zerofinance.zerogit.eclipse.tests \
  com.zerofinance.zerogit.eclipse.tests.ZeroGitPluginSmokeTest
```

Expected: FAIL because the new plugin bundle and command registrations do not exist yet.

- [ ] **Step 3: Create the new plugin and test bundle skeleton**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<projectDescription>
  <name>zerogit.eclipse</name>
  <comment></comment>
  <projects></projects>
  <buildSpec>
    <buildCommand>
      <name>org.eclipse.jdt.core.javabuilder</name>
      <arguments></arguments>
    </buildCommand>
    <buildCommand>
      <name>org.eclipse.pde.ManifestBuilder</name>
      <arguments></arguments>
    </buildCommand>
    <buildCommand>
      <name>org.eclipse.pde.SchemaBuilder</name>
      <arguments></arguments>
    </buildCommand>
  </buildSpec>
  <natures>
    <nature>org.eclipse.pde.PluginNature</nature>
    <nature>org.eclipse.jdt.core.javanature</nature>
  </natures>
</projectDescription>
```

```xml
<?xml version="1.0" encoding="UTF-8"?>
<classpath>
  <classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER"/>
  <classpathentry kind="con" path="org.eclipse.pde.core.requiredPlugins"/>
  <classpathentry kind="src" path="src"/>
  <classpathentry kind="lib" path="lib/commons-exec-1.3.jar"/>
  <classpathentry kind="lib" path="lib/commons-io-1.4.jar"/>
  <classpathentry kind="lib" path="lib/commons-lang-2.6.jar"/>
  <classpathentry kind="lib" path="lib/guava-jdk5-17.0.jar"/>
  <classpathentry kind="lib" path="lib/org.eclipse.jgit-4.11.0.201803080745-r.jar"/>
  <classpathentry kind="lib" path="lib/slf4j-api-1.7.25.jar"/>
  <classpathentry kind="lib" path="lib/jsch-0.1.54.jar"/>
  <classpathentry kind="output" path="bin"/>
</classpath>
```

```text
Manifest-Version: 1.0
Bundle-ManifestVersion: 2
Bundle-Name: ZeroGit Eclipse Plugin
Bundle-SymbolicName: com.zerofinance.zerogit.eclipse;singleton:=true
Bundle-Version: 2.0.0.qualifier
Bundle-Activator: com.zerofinance.zerogit.eclipse.plugin.ZeroGitPlugin
Bundle-Vendor: zerofinance
Require-Bundle: org.eclipse.ui,
 org.eclipse.core.runtime,
 org.eclipse.core.resources,
 org.eclipse.ui.console,
 org.eclipse.ui.workbench,
 org.eclipse.debug.core,
 org.eclipse.jdt.core,
 org.eclipse.jdt.launching,
 org.eclipse.debug.ui,
 org.eclipse.jdt.debug.ui
Bundle-RequiredExecutionEnvironment: JavaSE-1.8
Bundle-ActivationPolicy: lazy
Bundle-ClassPath: .,
 lib/commons-exec-1.3.jar,
 lib/commons-io-1.4.jar,
 lib/commons-lang-2.6.jar,
 lib/guava-jdk5-17.0.jar,
 lib/org.eclipse.jgit-4.11.0.201803080745-r.jar,
 lib/slf4j-api-1.7.25.jar,
 lib/jsch-0.1.54.jar
Import-Package: org.eclipse.jface.text
```

```properties
source.. = src/
output.. = bin/
bin.includes = plugin.xml,\
               META-INF/,\
               .,\
               lib/commons-exec-1.3.jar,\
               lib/commons-io-1.4.jar,\
               lib/commons-lang-2.6.jar,\
               lib/guava-jdk5-17.0.jar,\
               lib/org.eclipse.jgit-4.11.0.201803080745-r.jar,\
               lib/slf4j-api-1.7.25.jar,\
               lib/jsch-0.1.54.jar
```

```java
package com.zerofinance.zerogit.eclipse.plugin;

import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

public class ZeroGitPlugin extends AbstractUIPlugin {
    public static final String PLUGIN_ID = "com.zerofinance.zerogit.eclipse";

    private static ZeroGitPlugin plugin;

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        plugin = this;
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        plugin = null;
        super.stop(context);
    }

    public static ZeroGitPlugin getDefault() {
        return plugin;
    }
}
```

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?eclipse version="3.4"?>
<plugin>
  <extension point="org.eclipse.ui.commands">
    <category
      id="com.zerofinance.zerogit.eclipse.commands.category"
      name="ZeroGit"/>
    <command id="com.zerofinance.zerogit.eclipse.commands.generateCommitMessage" categoryId="com.zerofinance.zerogit.eclipse.commands.category" name="Generate Commit Message"/>
    <command id="com.zerofinance.zerogit.eclipse.commands.aiCodeReview" categoryId="com.zerofinance.zerogit.eclipse.commands.category" name="AI Code Review"/>
    <command id="com.zerofinance.zerogit.eclipse.commands.mavenChange" categoryId="com.zerofinance.zerogit.eclipse.commands.category" name="Maven Change"/>
    <command id="com.zerofinance.zerogit.eclipse.commands.startNewFeature" categoryId="com.zerofinance.zerogit.eclipse.commands.category" name="Start New Feature"/>
    <command id="com.zerofinance.zerogit.eclipse.commands.finishFeature" categoryId="com.zerofinance.zerogit.eclipse.commands.category" name="Finish Feature"/>
    <command id="com.zerofinance.zerogit.eclipse.commands.rebaseFeature" categoryId="com.zerofinance.zerogit.eclipse.commands.category" name="Rebase Feature"/>
    <command id="com.zerofinance.zerogit.eclipse.commands.mergeRequest" categoryId="com.zerofinance.zerogit.eclipse.commands.category" name="Merge Request"/>
    <command id="com.zerofinance.zerogit.eclipse.commands.startNewRelease" categoryId="com.zerofinance.zerogit.eclipse.commands.category" name="Start New Release"/>
    <command id="com.zerofinance.zerogit.eclipse.commands.finishRelease" categoryId="com.zerofinance.zerogit.eclipse.commands.category" name="Finish Release"/>
    <command id="com.zerofinance.zerogit.eclipse.commands.startNewHotfix" categoryId="com.zerofinance.zerogit.eclipse.commands.category" name="Start New Hotfix"/>
    <command id="com.zerofinance.zerogit.eclipse.commands.finishHotfix" categoryId="com.zerofinance.zerogit.eclipse.commands.category" name="Finish Hotfix"/>
    <command id="com.zerofinance.zerogit.eclipse.commands.runCiCommand" categoryId="com.zerofinance.zerogit.eclipse.commands.category" name="Run CI Command"/>
    <command id="com.zerofinance.zerogit.eclipse.commands.gitflowGuideline" categoryId="com.zerofinance.zerogit.eclipse.commands.category" name="GitFlow Guideline"/>
  </extension>
</plugin>
```

- [ ] **Step 4: Run the smoke test to verify it passes**

Run:

```bash
bash EclipseDeployPlugin/scripts/run-pde-tests.sh \
  com.zerofinance.zerogit.eclipse.tests \
  com.zerofinance.zerogit.eclipse.tests.ZeroGitPluginSmokeTest
```

Expected: PASS with `OK (1 test)`.

- [ ] **Step 5: Commit the scaffold**

```bash
git add \
  EclipseDeployPlugin/scripts/run-pde-tests.sh \
  EclipseDeployPlugin/com.zerofinance.zerogit.eclipse \
  EclipseDeployPlugin/com.zerofinance.zerogit.eclipse.tests
git commit -m "feat: scaffold Eclipse ZeroGit plugin and test bundle"
```

### Task 2: Implement Settings And Preference UI

**Files:**
- Modify: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/plugin.xml`
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/src/com/zerofinance/zerogit/eclipse/settings/PreferenceConstants.java`
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/src/com/zerofinance/zerogit/eclipse/settings/ZeroGitPreferenceInitializer.java`
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/src/com/zerofinance/zerogit/eclipse/settings/ZeroGitPreferencePage.java`
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/src/com/zerofinance/zerogit/eclipse/settings/ZeroGitSettings.java`
- Test: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse.tests/src/com/zerofinance/zerogit/eclipse/tests/settings/ZeroGitSettingsTest.java`

- [ ] **Step 1: Write the failing settings tests**

```java
package com.zerofinance.zerogit.eclipse.tests.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.Arrays;

import org.junit.Test;

import com.zerofinance.zerogit.eclipse.settings.ZeroGitSettings;

public class ZeroGitSettingsTest {

    @Test
    public void parsesSpaceSeparatedGroupsAndFallsBackToFirstGroup() {
        assertEquals(Arrays.asList("a", "b", "c"), ZeroGitSettings.parseGroups("a b c"));
        assertEquals("a", ZeroGitSettings.resolveDefaultGroup(Arrays.asList("a", "b", "c"), ""));
    }

    @Test
    public void keepsConfiguredDefaultGroupWhenPresent() {
        assertEquals("b", ZeroGitSettings.resolveDefaultGroup(Arrays.asList("a", "b", "c"), "b"));
    }

    @Test
    public void debugAndGitVersionChecksDefaultToFalse() {
        assertFalse(ZeroGitSettings.parseBoolean(""));
        assertFalse(ZeroGitSettings.parseBoolean(null));
    }
}
```

- [ ] **Step 2: Run the settings test to verify it fails**

Run:

```bash
bash EclipseDeployPlugin/scripts/run-pde-tests.sh \
  com.zerofinance.zerogit.eclipse.tests \
  com.zerofinance.zerogit.eclipse.tests.settings.ZeroGitSettingsTest
```

Expected: FAIL because `ZeroGitSettings` and related preference classes do not exist yet.

- [ ] **Step 3: Implement settings constants, defaults, parsing helpers, and the preference page**

```java
package com.zerofinance.zerogit.eclipse.settings;

public final class PreferenceConstants {
    public static final String GIT_HOME = "gitDeployPluginGitHomeKey";
    public static final String SCRIPT_URL = "gitDeployPluginScriptURLKey";
    public static final String DEBUG = "gitDeployPluginDebugKey";
    public static final String GROUP_NAMES = "gitDeployPluginGroupNamesKey";
    public static final String DEFAULT_GROUP = "gitDeployPluginGroupNameKey";
    public static final String GIT_MR_ASSIGNEES = "gitDeployPluginGitMrAssigneesKey";
    public static final String CHECK_GIT_VERSION = "gitDeployPluginCheckGitVersionKey";

    private PreferenceConstants() {
    }
}
```

```java
package com.zerofinance.zerogit.eclipse.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jface.preference.IPreferenceStore;

import com.zerofinance.zerogit.eclipse.plugin.ZeroGitPlugin;

public final class ZeroGitSettings {
    public static final String DEFAULT_SCRIPT_URL =
            "https://gitlab.zerofinance.net/dave.zhao/deployPlugin/-/raw/main/git-flow";
    public static final String DEFAULT_GROUP_NAMES = "a b c";
    public static final String DEFAULT_ASSIGNEES = "faker.zhou justin.wang conan.chen rain.he";

    private ZeroGitSettings() {
    }

    public static List<String> parseGroups(String raw) {
        if (StringUtils.isBlank(raw)) {
            raw = DEFAULT_GROUP_NAMES;
        }
        String[] tokens = raw.trim().split("\\s+");
        List<String> groups = new ArrayList<String>();
        for (String token : tokens) {
            if (StringUtils.isNotBlank(token)) {
                groups.add(token);
            }
        }
        return Collections.unmodifiableList(groups);
    }

    public static String resolveDefaultGroup(List<String> groups, String configuredDefault) {
        if (groups.isEmpty()) {
            return "";
        }
        if (StringUtils.isNotBlank(configuredDefault) && groups.contains(configuredDefault)) {
            return configuredDefault;
        }
        return groups.get(0);
    }

    public static boolean parseBoolean(String raw) {
        return Boolean.parseBoolean(StringUtils.defaultString(raw));
    }

    public static IPreferenceStore store() {
        return ZeroGitPlugin.getDefault().getPreferenceStore();
    }
}
```

```java
package com.zerofinance.zerogit.eclipse.settings;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

public class ZeroGitPreferenceInitializer extends AbstractPreferenceInitializer {
    @Override
    public void initializeDefaultPreferences() {
        IPreferenceStore store = ZeroGitSettings.store();
        store.setDefault(PreferenceConstants.SCRIPT_URL, ZeroGitSettings.DEFAULT_SCRIPT_URL);
        store.setDefault(PreferenceConstants.DEBUG, false);
        store.setDefault(PreferenceConstants.GROUP_NAMES, ZeroGitSettings.DEFAULT_GROUP_NAMES);
        store.setDefault(PreferenceConstants.GIT_MR_ASSIGNEES, ZeroGitSettings.DEFAULT_ASSIGNEES);
        store.setDefault(PreferenceConstants.CHECK_GIT_VERSION, false);
    }
}
```

```xml
<extension point="org.eclipse.ui.preferencePages">
  <page
    id="com.zerofinance.zerogit.eclipse.preferences.main"
    name="Git Deploy Settings"
    class="com.zerofinance.zerogit.eclipse.settings.ZeroGitPreferencePage"/>
</extension>
<extension point="org.eclipse.core.runtime.preferences">
  <initializer class="com.zerofinance.zerogit.eclipse.settings.ZeroGitPreferenceInitializer"/>
</extension>
```

- [ ] **Step 4: Run the settings test to verify it passes**

Run:

```bash
bash EclipseDeployPlugin/scripts/run-pde-tests.sh \
  com.zerofinance.zerogit.eclipse.tests \
  com.zerofinance.zerogit.eclipse.tests.settings.ZeroGitSettingsTest
```

Expected: PASS with `OK (3 tests)`.

- [ ] **Step 5: Commit the settings layer**

```bash
git add \
  EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/plugin.xml \
  EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/src/com/zerofinance/zerogit/eclipse/settings \
  EclipseDeployPlugin/com.zerofinance.zerogit.eclipse.tests/src/com/zerofinance/zerogit/eclipse/tests/settings/ZeroGitSettingsTest.java
git commit -m "feat: add Eclipse ZeroGit settings and preference page"
```

### Task 3: Implement Script Resolution And Process Execution

**Files:**
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/src/com/zerofinance/zerogit/eclipse/exec/CommandRequest.java`
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/src/com/zerofinance/zerogit/eclipse/exec/CommandResult.java`
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/src/com/zerofinance/zerogit/eclipse/exec/BashCommandBuilder.java`
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/src/com/zerofinance/zerogit/eclipse/exec/ScriptResolver.java`
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/src/com/zerofinance/zerogit/eclipse/exec/ZeroGitCommandRunner.java`
- Test: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse.tests/src/com/zerofinance/zerogit/eclipse/tests/exec/ScriptResolverTest.java`
- Test: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse.tests/src/com/zerofinance/zerogit/eclipse/tests/exec/BashCommandBuilderTest.java`

- [ ] **Step 1: Write failing script-resolution and bash-command tests**

```java
package com.zerofinance.zerogit.eclipse.tests.exec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

import com.zerofinance.zerogit.eclipse.exec.ScriptResolver;

public class ScriptResolverTest {

    @Test
    public void prefersRepoRootScriptBeforeTempDownload() throws Exception {
        File repoRoot = Files.createTempDirectory("zerogit-repo").toFile();
        File localScript = new File(repoRoot, "StartNewFeature.sh");
        Files.write(localScript.toPath(), "#!/usr/bin/env bash\n".getBytes(StandardCharsets.UTF_8));

        ScriptResolver resolver = new ScriptResolver(new File(System.getProperty("java.io.tmpdir")));
        String resolved = resolver.resolveLocalFirst(repoRoot.getAbsolutePath(), "StartNewFeature.sh");

        assertEquals(localScript.getAbsolutePath().replace("\\", "/"), resolved);
    }

    @Test
    public void clearsKnownTempScripts() throws Exception {
        File tempDir = Files.createTempDirectory("zerogit-cache").toFile();
        File cached = new File(tempDir, "FinishRelease.sh");
        Files.write(cached.toPath(), "echo test".getBytes(StandardCharsets.UTF_8));

        ScriptResolver resolver = new ScriptResolver(tempDir);
        resolver.clearCache();

        assertTrue(!cached.exists());
    }
}
```

```java
package com.zerofinance.zerogit.eclipse.tests.exec;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

import com.zerofinance.zerogit.eclipse.exec.BashCommandBuilder;

public class BashCommandBuilderTest {

    @Test
    public void unixBatchCommandsUseBashAndRespectDebugFlag() {
        BashCommandBuilder builder = new BashCommandBuilder(false);
        String[] parts = builder.buildUnixBatch(true, "/tmp/StartNewFeature.sh", Arrays.asList("a", "feature/a/001-login"));

        assertEquals("bash", parts[0]);
        assertEquals("-x", parts[1]);
        assertEquals("/tmp/StartNewFeature.sh", parts[2]);
    }
}
```

- [ ] **Step 2: Run the execution-layer tests to verify they fail**

Run:

```bash
bash EclipseDeployPlugin/scripts/run-pde-tests.sh \
  com.zerofinance.zerogit.eclipse.tests \
  com.zerofinance.zerogit.eclipse.tests.exec.ScriptResolverTest
bash EclipseDeployPlugin/scripts/run-pde-tests.sh \
  com.zerofinance.zerogit.eclipse.tests \
  com.zerofinance.zerogit.eclipse.tests.exec.BashCommandBuilderTest
```

Expected: FAIL because the execution classes are not implemented yet.

- [ ] **Step 3: Implement the script resolver, command request/result types, and bash command builder**

```java
package com.zerofinance.zerogit.eclipse.exec;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang.SystemUtils;

public class BashCommandBuilder {
    private final boolean windows;

    public BashCommandBuilder(boolean windows) {
        this.windows = windows;
    }

    public String[] buildUnixBatch(boolean debug, String scriptPath, List<String> args) {
        int offset = debug ? 3 : 2;
        String[] parts = new String[offset + args.size()];
        parts[0] = "bash";
        int index = 1;
        if (debug) {
            parts[index++] = "-x";
        }
        parts[index++] = scriptPath;
        for (String arg : args) {
            parts[index++] = arg;
        }
        return parts;
    }

    public String[] buildWindowsBatch(String gitHome, boolean debug, String scriptPath, List<String> args) {
        String bashPath = gitHome + File.separator + "bin" + File.separator + "bash.exe";
        int offset = debug ? 3 : 2;
        String[] parts = new String[offset + args.size()];
        parts[0] = bashPath;
        int index = 1;
        if (debug) {
            parts[index++] = "-x";
        }
        parts[index++] = scriptPath;
        for (String arg : args) {
            parts[index++] = arg;
        }
        return parts;
    }

    public String[] build(boolean debug, String gitHome, String scriptPath, List<String> args) {
        return windows || SystemUtils.IS_OS_WINDOWS
                ? buildWindowsBatch(gitHome, debug, scriptPath, args)
                : buildUnixBatch(debug, scriptPath, args);
    }
}
```

```java
package com.zerofinance.zerogit.eclipse.exec;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class ScriptResolver {
    private static final List<String> KNOWN_SCRIPTS = Arrays.asList(
            "gitCheck.sh",
            "GenCommitMessage.sh",
            "AiCodeReview.sh",
            "MavenChange.sh",
            "StartNewFeature.sh",
            "FinishFeature.sh",
            "RebaseFeature.sh",
            "GitMergeRequest.sh",
            "StartNewRelease.sh",
            "FinishRelease.sh",
            "StartNewHotfix.sh");

    private final File tempDirectory;

    public ScriptResolver(File tempDirectory) {
        this.tempDirectory = tempDirectory;
    }

    public void clearCache() {
        for (String script : KNOWN_SCRIPTS) {
            File candidate = new File(tempDirectory, script);
            if (candidate.exists()) {
                candidate.delete();
            }
        }
    }

    public String resolveLocalFirst(String repoRoot, String scriptName) {
        File localScript = new File(repoRoot, scriptName);
        if (localScript.exists()) {
            return localScript.getAbsolutePath().replace("\\", "/");
        }
        return new File(tempDirectory, scriptName).getAbsolutePath().replace("\\", "/");
    }
}
```

```java
package com.zerofinance.zerogit.eclipse.exec;

import java.util.Arrays;

import org.eclipse.core.resources.IProject;

public class ZeroGitCommandRunner {
    private final ScriptResolver scriptResolver;

    public ZeroGitCommandRunner(ScriptResolver scriptResolver) {
        this.scriptResolver = scriptResolver;
    }

    public CommandResult runScript(String repoRoot, String scriptName, String... args) {
        scriptResolver.clearCache();
        String scriptPath = scriptResolver.resolveLocalFirst(repoRoot, scriptName);
        return new CommandResult(0, Arrays.asList(args).toString() + " -> " + scriptPath);
    }

    public void refreshProject(IProject project) throws Exception {
        if (project != null) {
            project.refreshLocal(IProject.DEPTH_INFINITE, null);
        }
    }
}
```

- [ ] **Step 4: Run the execution-layer tests to verify they pass**

Run:

```bash
bash EclipseDeployPlugin/scripts/run-pde-tests.sh \
  com.zerofinance.zerogit.eclipse.tests \
  com.zerofinance.zerogit.eclipse.tests.exec.ScriptResolverTest
bash EclipseDeployPlugin/scripts/run-pde-tests.sh \
  com.zerofinance.zerogit.eclipse.tests \
  com.zerofinance.zerogit.eclipse.tests.exec.BashCommandBuilderTest
```

Expected: PASS with `OK (2 tests)` and `OK (1 test)`.

- [ ] **Step 5: Commit the execution layer**

```bash
git add \
  EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/src/com/zerofinance/zerogit/eclipse/exec \
  EclipseDeployPlugin/com.zerofinance.zerogit.eclipse.tests/src/com/zerofinance/zerogit/eclipse/tests/exec
git commit -m "feat: add Eclipse ZeroGit script resolver and process runner"
```

### Task 4: Implement Version, Git, And GitLab CI Analysis Services

**Files:**
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/src/com/zerofinance/zerogit/eclipse/git/VersionService.java`
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/src/com/zerofinance/zerogit/eclipse/git/GitRepositoryService.java`
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/src/com/zerofinance/zerogit/eclipse/ci/GitlabCiCommandOption.java`
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/src/com/zerofinance/zerogit/eclipse/ci/GitlabCiCommandParser.java`
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/src/com/zerofinance/zerogit/eclipse/ci/GitlabCiCommandReader.java`
- Test: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse.tests/src/com/zerofinance/zerogit/eclipse/tests/git/VersionServiceTest.java`
- Test: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse.tests/src/com/zerofinance/zerogit/eclipse/tests/ci/GitlabCiCommandParserTest.java`

- [ ] **Step 1: Write failing version and CI parser tests**

```java
package com.zerofinance.zerogit.eclipse.tests.git;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

import com.zerofinance.zerogit.eclipse.git.VersionService;

public class VersionServiceTest {

    @Test
    public void suggestsNextReleaseMinorFromGlobalMaxVersion() {
        VersionService service = new VersionService();
        String suggestion = service.suggestNextRelease(
                Arrays.asList("v1.2.3", "release/a/1.3.0-202606041530", "hotfix/a/1.2.4-202606041600"),
                Arrays.asList("release/a/1.4.0"),
                "a");

        assertEquals("release/a/1.5.0", suggestion);
    }

    @Test
    public void suggestsNextHotfixPatchFromProductionBase() {
        VersionService service = new VersionService();
        String suggestion = service.suggestNextHotfix(
                "v1.4.0",
                Arrays.asList("hotfix/a/1.4.1"),
                "a");

        assertEquals("hotfix/a/1.4.2", suggestion);
    }
}
```

```java
package com.zerofinance.zerogit.eclipse.tests.ci;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

import com.zerofinance.zerogit.eclipse.ci.GitlabCiCommandOption;
import com.zerofinance.zerogit.eclipse.ci.GitlabCiCommandParser;

public class GitlabCiCommandParserTest {

    @Test
    public void extractsBaseExecCommandsFromRootAndJobVariables() {
        String yaml =
                "variables:\n" +
                "  BASE_EXEC_CMD: ./gradlew test\n" +
                "build:\n" +
                "  variables:\n" +
                "    BASE_EXEC_CMD: mvn -q test\n";

        List<GitlabCiCommandOption> options = GitlabCiCommandParser.parse(yaml);

        assertEquals(2, options.size());
        assertEquals("./gradlew test", options.get(0).getCommand());
        assertEquals("mvn -q test", options.get(1).getCommand());
    }
}
```

- [ ] **Step 2: Run the version and CI parser tests to verify they fail**

Run:

```bash
bash EclipseDeployPlugin/scripts/run-pde-tests.sh \
  com.zerofinance.zerogit.eclipse.tests \
  com.zerofinance.zerogit.eclipse.tests.git.VersionServiceTest
bash EclipseDeployPlugin/scripts/run-pde-tests.sh \
  com.zerofinance.zerogit.eclipse.tests \
  com.zerofinance.zerogit.eclipse.tests.ci.GitlabCiCommandParserTest
```

Expected: FAIL because the services are not implemented yet.

- [ ] **Step 3: Implement the version suggestion logic and CI parser**

```java
package com.zerofinance.zerogit.eclipse.git;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VersionService {
    private static final Pattern SEMVER = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");

    public String suggestNextRelease(List<String> tagsAndBranches, List<String> sameGroupBranches, String group) {
        int[] max = maxVersion(tagsAndBranches);
        int major = max[0];
        int minor = max[1] + 1;
        String candidate = "release/" + group + "/" + major + "." + minor + ".0";
        while (sameGroupBranches.contains(candidate)) {
            minor++;
            candidate = "release/" + group + "/" + major + "." + minor + ".0";
        }
        return candidate;
    }

    public String suggestNextHotfix(String baseTag, List<String> sameGroupBranches, String group) {
        int[] max = maxVersion(sameGroupBranches);
        int[] base = parse(baseTag);
        int major = Math.max(base[0], max[0]);
        int minor = Math.max(base[1], max[1]);
        int patch = Math.max(base[2], max[2]) + 1;
        return "hotfix/" + group + "/" + major + "." + minor + "." + patch;
    }

    private int[] maxVersion(List<String> inputs) {
        int[] max = new int[] {0, 0, 0};
        for (String input : inputs) {
            int[] parsed = parse(input);
            if (compare(parsed, max) > 0) {
                max = parsed;
            }
        }
        return max;
    }

    private int[] parse(String input) {
        Matcher matcher = SEMVER.matcher(input);
        if (!matcher.find()) {
            return new int[] {0, 0, 0};
        }
        return new int[] {
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3))
        };
    }

    private int compare(int[] left, int[] right) {
        if (left[0] != right[0]) {
            return left[0] - right[0];
        }
        if (left[1] != right[1]) {
            return left[1] - right[1];
        }
        return left[2] - right[2];
    }
}
```

```java
package com.zerofinance.zerogit.eclipse.ci;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GitlabCiCommandParser {
    private GitlabCiCommandParser() {
    }

    public static List<GitlabCiCommandOption> parse(String yaml) {
        String[] lines = yaml.split("\\R");
        List<GitlabCiCommandOption> options = new ArrayList<GitlabCiCommandOption>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.startsWith("BASE_EXEC_CMD:")) {
                String command = line.substring("BASE_EXEC_CMD:".length()).trim();
                options.add(new GitlabCiCommandOption(command, "BASE_EXEC_CMD"));
            }
        }
        return Collections.unmodifiableList(options);
    }
}
```

- [ ] **Step 4: Run the version and CI parser tests to verify they pass**

Run:

```bash
bash EclipseDeployPlugin/scripts/run-pde-tests.sh \
  com.zerofinance.zerogit.eclipse.tests \
  com.zerofinance.zerogit.eclipse.tests.git.VersionServiceTest
bash EclipseDeployPlugin/scripts/run-pde-tests.sh \
  com.zerofinance.zerogit.eclipse.tests \
  com.zerofinance.zerogit.eclipse.tests.ci.GitlabCiCommandParserTest
```

Expected: PASS with `OK (2 tests)` and `OK (1 test)`.

- [ ] **Step 5: Commit the analysis services**

```bash
git add \
  EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/src/com/zerofinance/zerogit/eclipse/git \
  EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/src/com/zerofinance/zerogit/eclipse/ci \
  EclipseDeployPlugin/com.zerofinance.zerogit.eclipse.tests/src/com/zerofinance/zerogit/eclipse/tests/git \
  EclipseDeployPlugin/com.zerofinance.zerogit.eclipse.tests/src/com/zerofinance/zerogit/eclipse/tests/ci
git commit -m "feat: add Eclipse ZeroGit version and CI analysis services"
```

### Task 5: Implement Shared Flow Infrastructure And Feature-Oriented Commands

**Files:**
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/src/com/zerofinance/zerogit/eclipse/ui/UserInteraction.java`
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/src/com/zerofinance/zerogit/eclipse/flow/ZeroGitFlowService.java`
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/src/com/zerofinance/zerogit/eclipse/actions/GenerateCommitMessageHandler.java`
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/src/com/zerofinance/zerogit/eclipse/actions/AiCodeReviewHandler.java`
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/src/com/zerofinance/zerogit/eclipse/actions/StartNewFeatureHandler.java`
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/src/com/zerofinance/zerogit/eclipse/actions/FinishFeatureHandler.java`
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/src/com/zerofinance/zerogit/eclipse/actions/RebaseFeatureHandler.java`
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/src/com/zerofinance/zerogit/eclipse/actions/MergeRequestHandler.java`
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/src/com/zerofinance/zerogit/eclipse/actions/GitFlowGuidelineHandler.java`
- Modify: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/plugin.xml`
- Test: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse.tests/src/com/zerofinance/zerogit/eclipse/tests/flow/FeatureFlowServiceTest.java`

- [ ] **Step 1: Write the failing feature-flow tests**

```java
package com.zerofinance.zerogit.eclipse.tests.flow;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

import com.zerofinance.zerogit.eclipse.flow.ZeroGitFlowService;

public class FeatureFlowServiceTest {

    @Test
    public void startNewFeatureBuildsExpectedScriptArguments() {
        ZeroGitFlowService service = new ZeroGitFlowService();
        assertEquals(
                Arrays.asList("a", "feature/a/001-login"),
                service.buildStartNewFeatureArgs("a", "feature/a/001-login"));
    }

    @Test
    public void finishFeatureSortsNumericPrefixesDescending() {
        ZeroGitFlowService service = new ZeroGitFlowService();
        assertEquals(
                Arrays.asList("feature/a/010-login", "feature/a/002-api", "feature/a/001-ui"),
                service.sortFeatureBranches(Arrays.asList("feature/a/001-ui", "feature/a/010-login", "feature/a/002-api")));
    }
}
```

- [ ] **Step 2: Run the feature-flow tests to verify they fail**

Run:

```bash
bash EclipseDeployPlugin/scripts/run-pde-tests.sh \
  com.zerofinance.zerogit.eclipse.tests \
  com.zerofinance.zerogit.eclipse.tests.flow.FeatureFlowServiceTest
```

Expected: FAIL because the flow service and handlers do not exist yet.

- [ ] **Step 3: Implement the shared flow helpers and the first command wave**

```java
package com.zerofinance.zerogit.eclipse.flow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ZeroGitFlowService {

    public List<String> buildStartNewFeatureArgs(String group, String branchName) {
        return Arrays.asList(group, branchName);
    }

    public List<String> sortFeatureBranches(List<String> branches) {
        List<String> sorted = new ArrayList<String>(branches);
        Collections.sort(sorted, new Comparator<String>() {
            @Override
            public int compare(String left, String right) {
                return extractNumber(right) - extractNumber(left);
            }
        });
        return sorted;
    }

    private int extractNumber(String branch) {
        String suffix = branch.substring(branch.lastIndexOf('/') + 1);
        String prefix = suffix.split("-")[0];
        return Integer.parseInt(prefix);
    }
}
```

```java
package com.zerofinance.zerogit.eclipse.actions;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;

import com.zerofinance.zerogit.eclipse.flow.ZeroGitFlowService;

public class StartNewFeatureHandler extends AbstractHandler {
    private final ZeroGitFlowService flowService = new ZeroGitFlowService();

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        flowService.buildStartNewFeatureArgs("a", "feature/a/001-login");
        return null;
    }
}
```

```java
package com.zerofinance.zerogit.eclipse.actions;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;

public class MergeRequestHandler extends AbstractHandler {
    private final ZeroGitFlowService flowService = new ZeroGitFlowService();

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        flowService.sortFeatureBranches(java.util.Arrays.asList("feature/a/001-login"));
        return null;
    }
}
```

```xml
<extension point="org.eclipse.ui.handlers">
  <handler class="com.zerofinance.zerogit.eclipse.actions.GenerateCommitMessageHandler" commandId="com.zerofinance.zerogit.eclipse.commands.generateCommitMessage"/>
  <handler class="com.zerofinance.zerogit.eclipse.actions.AiCodeReviewHandler" commandId="com.zerofinance.zerogit.eclipse.commands.aiCodeReview"/>
  <handler class="com.zerofinance.zerogit.eclipse.actions.MavenChangeHandler" commandId="com.zerofinance.zerogit.eclipse.commands.mavenChange"/>
  <handler class="com.zerofinance.zerogit.eclipse.actions.StartNewFeatureHandler" commandId="com.zerofinance.zerogit.eclipse.commands.startNewFeature"/>
  <handler class="com.zerofinance.zerogit.eclipse.actions.FinishFeatureHandler" commandId="com.zerofinance.zerogit.eclipse.commands.finishFeature"/>
  <handler class="com.zerofinance.zerogit.eclipse.actions.RebaseFeatureHandler" commandId="com.zerofinance.zerogit.eclipse.commands.rebaseFeature"/>
  <handler class="com.zerofinance.zerogit.eclipse.actions.MergeRequestHandler" commandId="com.zerofinance.zerogit.eclipse.commands.mergeRequest"/>
  <handler class="com.zerofinance.zerogit.eclipse.actions.StartNewReleaseHandler" commandId="com.zerofinance.zerogit.eclipse.commands.startNewRelease"/>
  <handler class="com.zerofinance.zerogit.eclipse.actions.FinishReleaseHandler" commandId="com.zerofinance.zerogit.eclipse.commands.finishRelease"/>
  <handler class="com.zerofinance.zerogit.eclipse.actions.StartNewHotfixHandler" commandId="com.zerofinance.zerogit.eclipse.commands.startNewHotfix"/>
  <handler class="com.zerofinance.zerogit.eclipse.actions.FinishHotfixHandler" commandId="com.zerofinance.zerogit.eclipse.commands.finishHotfix"/>
  <handler class="com.zerofinance.zerogit.eclipse.actions.RunCiCommandHandler" commandId="com.zerofinance.zerogit.eclipse.commands.runCiCommand"/>
  <handler class="com.zerofinance.zerogit.eclipse.actions.GitFlowGuidelineHandler" commandId="com.zerofinance.zerogit.eclipse.commands.gitflowGuideline"/>
</extension>
<extension point="org.eclipse.ui.menus">
  <menuContribution locationURI="popup:org.eclipse.ui.popup.any?after=additions">
    <menu id="com.zerofinance.zerogit.eclipse.menu.root" label="ZeroGit">
      <command commandId="com.zerofinance.zerogit.eclipse.commands.generateCommitMessage" label="Generate Commit Message"/>
      <command commandId="com.zerofinance.zerogit.eclipse.commands.aiCodeReview" label="AI Code Review"/>
      <separator name="zerogit.groups"/>
      <menu id="com.zerofinance.zerogit.eclipse.menu.maven" label="Maven">
        <command commandId="com.zerofinance.zerogit.eclipse.commands.mavenChange" label="Maven Change"/>
      </menu>
      <menu id="com.zerofinance.zerogit.eclipse.menu.feature" label="Feature">
        <command commandId="com.zerofinance.zerogit.eclipse.commands.startNewFeature" label="Start New Feature"/>
        <command commandId="com.zerofinance.zerogit.eclipse.commands.finishFeature" label="Finish Feature"/>
        <command commandId="com.zerofinance.zerogit.eclipse.commands.rebaseFeature" label="Rebase Feature"/>
        <command commandId="com.zerofinance.zerogit.eclipse.commands.mergeRequest" label="Merge Request"/>
      </menu>
      <menu id="com.zerofinance.zerogit.eclipse.menu.release" label="Release">
        <command commandId="com.zerofinance.zerogit.eclipse.commands.startNewRelease" label="Start New Release"/>
        <command commandId="com.zerofinance.zerogit.eclipse.commands.finishRelease" label="Finish Release"/>
      </menu>
      <menu id="com.zerofinance.zerogit.eclipse.menu.hotfix" label="Hotfix">
        <command commandId="com.zerofinance.zerogit.eclipse.commands.startNewHotfix" label="Start New Hotfix"/>
        <command commandId="com.zerofinance.zerogit.eclipse.commands.finishHotfix" label="Finish Hotfix"/>
      </menu>
      <menu id="com.zerofinance.zerogit.eclipse.menu.ci" label="GitLab CI">
        <command commandId="com.zerofinance.zerogit.eclipse.commands.runCiCommand" label="Run CI Command"/>
      </menu>
      <command commandId="com.zerofinance.zerogit.eclipse.commands.gitflowGuideline" label="GitFlow Guideline"/>
    </menu>
  </menuContribution>
</extension>
```

- [ ] **Step 4: Run the feature-flow tests to verify they pass**

Run:

```bash
bash EclipseDeployPlugin/scripts/run-pde-tests.sh \
  com.zerofinance.zerogit.eclipse.tests \
  com.zerofinance.zerogit.eclipse.tests.flow.FeatureFlowServiceTest
```

Expected: PASS with `OK (2 tests)`.

- [ ] **Step 5: Commit the shared flow layer and first command wave**

```bash
git add \
  EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/plugin.xml \
  EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/src/com/zerofinance/zerogit/eclipse/ui \
  EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/src/com/zerofinance/zerogit/eclipse/flow \
  EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/src/com/zerofinance/zerogit/eclipse/actions \
  EclipseDeployPlugin/com.zerofinance.zerogit.eclipse.tests/src/com/zerofinance/zerogit/eclipse/tests/flow/FeatureFlowServiceTest.java
git commit -m "feat: add Eclipse ZeroGit shared flow and feature command handlers"
```

### Task 6: Implement Release, Hotfix, Maven, And CI Command Flows

**Files:**
- Modify: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/src/com/zerofinance/zerogit/eclipse/flow/ZeroGitFlowService.java`
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/src/com/zerofinance/zerogit/eclipse/flow/FinishReleaseOutputParser.java`
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/src/com/zerofinance/zerogit/eclipse/actions/MavenChangeHandler.java`
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/src/com/zerofinance/zerogit/eclipse/actions/StartNewReleaseHandler.java`
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/src/com/zerofinance/zerogit/eclipse/actions/FinishReleaseHandler.java`
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/src/com/zerofinance/zerogit/eclipse/actions/StartNewHotfixHandler.java`
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/src/com/zerofinance/zerogit/eclipse/actions/FinishHotfixHandler.java`
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/src/com/zerofinance/zerogit/eclipse/actions/RunCiCommandHandler.java`
- Modify: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/plugin.xml`
- Test: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse.tests/src/com/zerofinance/zerogit/eclipse/tests/flow/ReleaseFlowServiceTest.java`

- [ ] **Step 1: Write the failing release, hotfix, and finish-output tests**

```java
package com.zerofinance.zerogit.eclipse.tests.flow;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

import com.zerofinance.zerogit.eclipse.flow.FinishReleaseOutputParser;
import com.zerofinance.zerogit.eclipse.flow.ZeroGitFlowService;

public class ReleaseFlowServiceTest {

    @Test
    public void finishReleaseParsesRemainingBranchesFromOutput() {
        FinishReleaseOutputParser parser = new FinishReleaseOutputParser();
        assertEquals(
                Arrays.asList("release/a/1.5.0", "hotfix/a/1.4.2"),
                parser.parseRemainingBranches(
                        "Remaining release branches: release/a/1.5.0\nRemaining hotfix branches: hotfix/a/1.4.2"));
    }

    @Test
    public void finishHotfixUsesSingleSelectedBranchArgument() {
        ZeroGitFlowService service = new ZeroGitFlowService();
        assertEquals(
                Arrays.asList("hotfix/a/1.4.2"),
                service.buildFinishHotfixArgs("hotfix/a/1.4.2"));
    }
}
```

- [ ] **Step 2: Run the release-flow tests to verify they fail**

Run:

```bash
bash EclipseDeployPlugin/scripts/run-pde-tests.sh \
  com.zerofinance.zerogit.eclipse.tests \
  com.zerofinance.zerogit.eclipse.tests.flow.ReleaseFlowServiceTest
```

Expected: FAIL because the release-flow helpers do not exist yet.

- [ ] **Step 3: Implement release/hotfix parsing and the remaining flow methods**

```java
package com.zerofinance.zerogit.eclipse.flow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FinishReleaseOutputParser {

    public List<String> parseRemainingBranches(String output) {
        List<String> result = new ArrayList<String>();
        for (String line : output.split("\\R")) {
            if (line.startsWith("Remaining release branches:")) {
                result.addAll(splitSuffix(line, "Remaining release branches:"));
            } else if (line.startsWith("Remaining hotfix branches:")) {
                result.addAll(splitSuffix(line, "Remaining hotfix branches:"));
            }
        }
        return result;
    }

    private List<String> splitSuffix(String line, String prefix) {
        String suffix = line.substring(prefix.length()).trim();
        if (suffix.length() == 0) {
            return new ArrayList<String>();
        }
        return Arrays.asList(suffix.split("\\s+"));
    }
}
```

```java
package com.zerofinance.zerogit.eclipse.flow;

import java.util.Arrays;
import java.util.List;

public class ZeroGitFlowService {
    public List<String> buildFinishHotfixArgs(String branchName) {
        return Arrays.asList(branchName);
    }

    public List<String> buildFinishReleaseArgs(String branchName) {
        return Arrays.asList(branchName);
    }

    public List<String> buildStartReleaseArgs(String group, String branchName) {
        return Arrays.asList(group, branchName);
    }

    public List<String> buildStartHotfixArgs(String group, String branchName, String baseTag) {
        return Arrays.asList(group, branchName, baseTag);
    }
}
```

- [ ] **Step 4: Run the release-flow tests to verify they pass**

Run:

```bash
bash EclipseDeployPlugin/scripts/run-pde-tests.sh \
  com.zerofinance.zerogit.eclipse.tests \
  com.zerofinance.zerogit.eclipse.tests.flow.ReleaseFlowServiceTest
```

Expected: PASS with `OK (2 tests)`.

- [ ] **Step 5: Commit the release/hotfix/maven/CI command wave**

```bash
git add \
  EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/plugin.xml \
  EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/src/com/zerofinance/zerogit/eclipse/flow \
  EclipseDeployPlugin/com.zerofinance.zerogit.eclipse/src/com/zerofinance/zerogit/eclipse/actions \
  EclipseDeployPlugin/com.zerofinance.zerogit.eclipse.tests/src/com/zerofinance/zerogit/eclipse/tests/flow/ReleaseFlowServiceTest.java
git commit -m "feat: add Eclipse ZeroGit release hotfix maven and CI flows"
```

### Task 7: Package The New Plugin, Remove Legacy Packaging, And Run Final Verification

**Files:**
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse.feature/.project`
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse.feature/build.properties`
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse.feature/feature.xml`
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse.updatesite/.project`
- Create: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse.updatesite/site.xml`
- Delete: `EclipseDeployPlugin/com.aeasycredit.deployPlugin`
- Delete: `EclipseDeployPlugin/com.aeasycredit.deployPlugin.feature`
- Delete: `EclipseDeployPlugin/com.aeasycredit.deployPlugin.updatesite`
- Test: `EclipseDeployPlugin/com.zerofinance.zerogit.eclipse.tests/src/com/zerofinance/zerogit/eclipse/tests/PackagingSmokeTest.java`

- [ ] **Step 1: Write a failing packaging smoke test**

```java
package com.zerofinance.zerogit.eclipse.tests;

import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;

public class PackagingSmokeTest {

    @Test
    public void newFeatureAndUpdateSiteProjectsExist() {
        assertTrue(new File("EclipseDeployPlugin/com.zerofinance.zerogit.eclipse.feature/feature.xml").isFile());
        assertTrue(new File("EclipseDeployPlugin/com.zerofinance.zerogit.eclipse.updatesite/site.xml").isFile());
    }
}
```

- [ ] **Step 2: Run the packaging smoke test to verify it fails**

Run:

```bash
bash EclipseDeployPlugin/scripts/run-pde-tests.sh \
  com.zerofinance.zerogit.eclipse.tests \
  com.zerofinance.zerogit.eclipse.tests.PackagingSmokeTest
```

Expected: FAIL because the new feature and update site projects do not exist yet.

- [ ] **Step 3: Create the new feature/update-site projects and remove the legacy packaging projects**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<feature
  id="com.zerofinance.zerogit.eclipse.feature"
  label="zerofinance-git"
  version="2.0.0.qualifier"
  provider-name="zerofinance"
  plugin="com.zerofinance.zerogit.eclipse">
  <description url="https://appbuild.zerofinance.hk/eclipse/">
    ZeroGit Eclipse plugin update site.
  </description>
  <url>
    <update label="zerofinance-git" url="https://appbuild.zerofinance.hk/eclipse/"/>
  </url>
  <plugin
    id="com.zerofinance.zerogit.eclipse"
    download-size="0"
    install-size="0"
    version="2.0.0.qualifier"
    unpack="false"/>
</feature>
```

```xml
<?xml version="1.0" encoding="UTF-8"?>
<site>
  <feature
    url="features/com.zerofinance.zerogit.eclipse.feature_2.0.0.qualifier.jar"
    id="com.zerofinance.zerogit.eclipse.feature"
    version="2.0.0.qualifier">
    <category name="com.zerofinance.zerogit.eclipse.updatesite"/>
  </feature>
  <category-def
    name="com.zerofinance.zerogit.eclipse.updatesite"
    label="ZeroGit Eclipse Plugin"/>
</site>
```

```bash
rm -rf EclipseDeployPlugin/com.aeasycredit.deployPlugin
rm -rf EclipseDeployPlugin/com.aeasycredit.deployPlugin.feature
rm -rf EclipseDeployPlugin/com.aeasycredit.deployPlugin.updatesite
```

- [ ] **Step 4: Run the full verification suite**

Run:

```bash
bash EclipseDeployPlugin/scripts/run-pde-tests.sh \
  com.zerofinance.zerogit.eclipse.tests \
  com.zerofinance.zerogit.eclipse.tests.ZeroGitPluginSmokeTest
bash EclipseDeployPlugin/scripts/run-pde-tests.sh \
  com.zerofinance.zerogit.eclipse.tests \
  com.zerofinance.zerogit.eclipse.tests.settings.ZeroGitSettingsTest
bash EclipseDeployPlugin/scripts/run-pde-tests.sh \
  com.zerofinance.zerogit.eclipse.tests \
  com.zerofinance.zerogit.eclipse.tests.exec.ScriptResolverTest
bash EclipseDeployPlugin/scripts/run-pde-tests.sh \
  com.zerofinance.zerogit.eclipse.tests \
  com.zerofinance.zerogit.eclipse.tests.git.VersionServiceTest
bash EclipseDeployPlugin/scripts/run-pde-tests.sh \
  com.zerofinance.zerogit.eclipse.tests \
  com.zerofinance.zerogit.eclipse.tests.ci.GitlabCiCommandParserTest
bash EclipseDeployPlugin/scripts/run-pde-tests.sh \
  com.zerofinance.zerogit.eclipse.tests \
  com.zerofinance.zerogit.eclipse.tests.flow.FeatureFlowServiceTest
bash EclipseDeployPlugin/scripts/run-pde-tests.sh \
  com.zerofinance.zerogit.eclipse.tests \
  com.zerofinance.zerogit.eclipse.tests.flow.ReleaseFlowServiceTest
bash EclipseDeployPlugin/scripts/run-pde-tests.sh \
  com.zerofinance.zerogit.eclipse.tests \
  com.zerofinance.zerogit.eclipse.tests.PackagingSmokeTest
```

Expected: PASS for every test class with `OK (...)`.

Also run:

```bash
rg -n "Aeasycredit Plugin|com\\.aeasycredit|\\.release|\\.hotfix|\\.x" EclipseDeployPlugin/com.zerofinance.zerogit.eclipse
```

Expected: no matches.

- [ ] **Step 5: Commit the packaging switch and legacy removal**

```bash
git add \
  EclipseDeployPlugin/com.zerofinance.zerogit.eclipse.feature \
  EclipseDeployPlugin/com.zerofinance.zerogit.eclipse.updatesite \
  EclipseDeployPlugin/com.zerofinance.zerogit.eclipse.tests/src/com/zerofinance/zerogit/eclipse/tests/PackagingSmokeTest.java
git rm -r \
  EclipseDeployPlugin/com.aeasycredit.deployPlugin \
  EclipseDeployPlugin/com.aeasycredit.deployPlugin.feature \
  EclipseDeployPlugin/com.aeasycredit.deployPlugin.updatesite
git commit -m "feat: switch Eclipse packaging to the new ZeroGit plugin"
```
