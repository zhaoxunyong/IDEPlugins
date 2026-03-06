# ZeroGit JetBrains IDEA 插件 — 产品需求文档 (idea-prd)

本文档面向 **IdeaDeployPlugin**（Java / IntelliJ Platform）项目，在功能上与 [VscodeDeployPlugin/prd.md](../VscodeDeployPlugin/prd.md) 对齐，实现基于 Git Flow 的 7 个 ZeroGit 命令；技术描述采用 JetBrains/IDEA 术语与 API，便于直接落地开发。

---

## 约束说明：scripts 脚本复用

- **脚本与 VS Code 插件共用**：使用 VscodeDeployPlugin 仓库下 `scripts/` 目录中的脚本，**不修改任何脚本代码**。
- 脚本的入参、输出格式、执行环境（Bash、工作目录、参数顺序）以现有脚本为准；IDEA 插件只负责：
  - 配置（groupName、脚本 URL、Git 路径等）
  - 弹窗输入/选择（分支名、版本、要结束的分支等）
  - 解析脚本路径（项目根优先，否则从配置 URL 下载）
  - 调用脚本（终端或同步执行）并解析输出（如 `REMAINING_RELEASES:`）。

---

## 1. 项目概述

### 1.1 定位

- **产品名称**：Zero Git Deploy Toolkit（与现有 plugin.xml 名称一致，可保留或改为 ZeroGit）
- **类型**：基于 Git Flow 的 IntelliJ Platform 插件，面向多分组（如 a/b）的 feature / release / hotfix 分支管理。
- **核心价值**：在 IDEA 内通过菜单/工具栏触发 7 个标准化流程（Start/Finish Feature、Rebase Feature、Start/Finish Release、Start/Finish Hotfix），减少手工敲命令与误操作；脚本与 VS Code 插件完全一致，便于跨 IDE 统一流程。

### 1.2 技术栈（IDEA 侧）

- **语言 / 构建**：Java 11+，Gradle（Kotlin DSL），`org.jetbrains.intellij` 插件。
- **依赖**：IntelliJ Platform API、`org.jetbrains.plugins.terminal`、可选 `git4idea` 或命令行 Git 调用；脚本下载可用 `java.net.URL`/`HttpURLConnection` 或现有 `CommandUtils.processScript` 思路。
- **脚本执行**：Bash；Windows 下使用配置的 Git Bash 路径（与现有 `ZeroGitDeploySetting.getGitHome()` 一致）。

### 1.3 与现有 IdeaDeployPlugin 的关系

- **保留可复用**：`ZeroGitDeploySetting`（Git Home、Script URL、Debug 等）、`DeployCmdExecuter` 的同步/终端执行方式、`CommandUtils.getRootProjectPath`、Tool Window（Git Deploy）、Notification、Console 输出。
- **需要新增/改造**：
  - 配置项：**groupName**（必选，枚举 a/b）；可选 **checkGitVersion**。
  - 7 个独立 Action：StartNewFeature、FinishFeature、RebaseFeature、StartNewRelease、FinishRelease、StartNewHotfix、FinishHotfix（与 VS Code 命令一一对应）。
  - 脚本名与 VS Code 对齐：`StartNewFeature.sh`、`FinishFeature.sh`、`RebaseFeature.sh`、`StartNewRelease.sh`、`FinishRelease.sh`、`StartNewHotfix.sh`、`FinishHotfix.sh`，以及执行前 `gitCheck.sh`。
  - 分支/版本逻辑：feature 命名规则、release/hotfix SemVer、建议版本计算、剩余分支解析等（见下文）。

---

## 2. 核心概念

### 2.1 分组（Group）

- 所有 7 个操作都**必须**先配置 **groupName**，且只能为预定义枚举值（如 `a`、`b`）。
- 分组用于：
  - 分支前缀：`feature/<group>/`、`release/<group>/`、`hotfix/<group>/`
  - develop 分支名：`develop-<group>`（如 `develop-a`、`develop-b`）
- 未配置或非法时：提示「请先配置 groupName 为 a 或 b」，并引导打开 **Settings → Git Deploy Settings**（或 ZeroGit 配置页）。

### 2.2 分支命名规范

| 类型    | 前缀格式            | 名称/版本规则             | 示例                  |
|---------|---------------------|---------------------------|-----------------------|
| feature | `feature/<group>/`   | 数字-描述，如 `001-login` | `feature/a/001-login` |
| release | `release/<group>/`  | SemVer：`X.Y.Z`           | `release/a/1.0.0`     |
| hotfix  | `hotfix/<group>/`   | SemVer：`X.Y.Z`           | `hotfix/a/1.0.1`      |

- **develop**：`develop-<group>`。**master**：主发布分支，release/hotfix 完成后合并到 master 并打 tag（如 `v1.0.0`）。

### 2.3 Git Flow 流程简述

- **Feature**：从 `develop-<group>` 拉取 `feature/<group>/xxx`，开发完成后在 GitLab MR 到 `develop-<group>` 并 Merge，再在插件中「Finish Feature」删除本地 feature 分支。
- **Release**：从 develop 拉取 `release/<group>/X.Y.Z`，测试通过后「Finish Release」：合并到 master、打 tag、删除 release 分支、将 master 合并回各 develop 及未完成的 release/hotfix 分支。
- **Hotfix**：从 master 拉取 `hotfix/<group>/X.Y.Z`，修完后「Finish Hotfix」：合并回 master、打 tag、同步回 develop 等。

---

## 3. 配置项（IDEA 实现）

建议在 **Settings → Git Deploy Settings**（`ZeroGitDeploySetting`）中提供下表项；与 VS Code 的 `zerofinanceGit.*` 对应关系已注明。

| 配置项说明           | 存储 Key（示例）     | 类型   | 默认值 | 说明 |
|----------------------|----------------------|--------|--------|------|
| 脚本根 URL           | SCRIPT_URL_KEY       | String | 指定 GitLab raw 地址 | 对应 `zerofinanceGit.gitScriptsUrlPreference`，末尾无 `/`。 |
| Git 主目录（Windows）| GIT_HOME_KEY         | String | 空     | 用于 bash.exe 路径；Windows 必填。 |
| 分组 groupName       | GROUP_NAME_KEY       | String | ""     | **必选**。枚举："" \| "a" \| "b"。对应 `zerofinanceGit.groupName`。 |
| 检查 Git 版本        | CHECK_GIT_VERSION_KEY| Boolean| true   | 对应 `zerofinanceGit.checkGitVersion`，要求 ≥ 2.29。 |
| Debug                | DEBUG_KEY            | Boolean| false  | 对应 `zerofinanceGit.debug`；开启时脚本执行带 `bash -x`，日志可打至 IDEA 的 Event Log / 控制台。 |
| 在终端中运行         | RUNNING_IN_TERMINAL_KEY | Boolean | 见现有 | 与现有一致：部分命令在 Terminal 中执行，部分同步执行。 |

现有 **Maven Repo URL**、**Skip Repo Change Confirm**、**More Details** 等可保留，与 ZeroGit 流程无冲突即可。

---

## 4. 命令与功能清单（7 个 Action）

以下 7 个功能与 VS Code 的 7 个命令一一对应；IDEA 侧以 **AnAction** 形式注册，并在菜单/工具栏中展示。

### 4.1 ZeroGit: Start New Feature

- **脚本**：`StartNewFeature.sh`
- **流程**：
  1. 校验 groupName 已配置（否则提示并打开设置）。
  2. 弹窗输入 feature 名称：默认/占位符带 `feature/<group>/`，校验：必须以该前缀开头，后缀匹配 `^\d+-\S.*$`（如 `001-login`）。
  3. 确定 Git 根目录：当前 Project 的 Git 根，或多模块时可选 Module/根（与「选择工作区」等价）。
  4. 执行 **gitCheck**（见第 6 节）。
  5. 解析脚本路径：项目根下存在 `StartNewFeature.sh` 则用本地，否则从配置 URL 下载到临时目录。
  6. 确认执行对话框：展示命令名、工作目录、脚本名、参数。
  7. **在终端执行**：`<bash> <script> <groupName> <fullFeatureName>`。
- **脚本参数**：`[groupName, fullFeatureName]`。

### 4.2 ZeroGit: Finish Feature

- **脚本**：`FinishFeature.sh`
- **流程**：
  1. 校验 groupName。
  2. **确认**：「是否已在 GitLab 中 MR 到 develop-<group> 并完成 Merge？继续只会删除本地 feature 分支。」→ 否则中止。
  3. 确定 Git 根 → gitCheck → 解析脚本。
  4. 从**本地**分支列表选择要结束的 feature 分支（仅 `feature/<group>/` 前缀，按数字前缀降序）；无则提示并中止。
  5. 再次确认执行 → **在终端执行**脚本。
- **脚本参数**：`[groupName, selectedFeatureBranch]`。

### 4.3 ZeroGit: Rebase Feature

- **脚本**：`RebaseFeature.sh`
- **流程**：
  1. 校验 groupName，确定 Git 根，gitCheck，解析脚本。
  2. 获取当前分支（如通过 `GitRepositoryManager` 或 `git rev-parse --abbrev-ref HEAD`）；若非 `feature/<group>/` 开头，报错并中止。
  3. 确认执行 → **在终端执行**，参数为当前分支名。
- **脚本参数**：`[groupName, currentBranch]`。

### 4.4 ZeroGit: Start New Release

- **脚本**：`StartNewRelease.sh`
- **流程**：
  1. 校验 groupName。
  2. **确认**：「是否已执行 Finish Feature 操作？」→ 否则中止。
  3. 确定 Git 根 → gitCheck → 解析脚本。
  4. 输入 release 版本（带 `release/<group>/` 前缀）：建议版本 = max(远程最新 release 版本, 最新 hotfix 版本, 最新 tag 版本) 的 patch+1；若无则 `1.0.0`。校验：SemVer；不与已有 release 重名；不与已有 hotfix 版本冲突。
  5. 确认执行 → **在终端执行**。
- **脚本参数**：`[groupName, fullReleaseName]`。

### 4.5 ZeroGit: Finish Release

- **脚本**：`FinishRelease.sh`
- **流程**：
  1. 校验 groupName。
  2. **确认 1**：「只有 Maintainer 才有权限，请确认你有 Maintainer 权限？」→ 否则中止。
  3. **确认 2**：「运维是否已完成上线？」→ 否则中止。
  4. 确定 Git 根 → gitCheck → 解析脚本。
  5. 从远程（及本地）release 分支列表选择要结束的分支（按版本降序）。
  6. **确认 3**：展示命令、工作目录、脚本、参数 → 确认执行。
  7. **同步执行**脚本（如 `DeployCmdExecuter.exec`），以便解析 stdout/stderr。
  8. 从输出中解析 `REMAINING_RELEASES:` 或 `Remaining release branches:`；若有剩余分支，再弹窗：「目前有进行中的 xxx 分支，请项目经理评估是否需要重新测试？」
  9. 失败时提示「FinishRelease 失败，请通过日志查看具体原因」。
- **脚本参数**：`[groupName, selectedReleaseBranch, groupsList]`，groupsList 为所有有效 group 逗号拼接（如 `a,b`）。

### 4.6 ZeroGit: Start New Hotfix

- **脚本**：`StartNewHotfix.sh`
- **流程**：
  1. 校验 groupName，确定 Git 根，gitCheck，解析脚本。
  2. 输入 hotfix 版本（带 `hotfix/<group>/` 前缀）：建议版本逻辑同 release；校验 SemVer、不与已有 hotfix 重名、不与 release 版本冲突。
  3. 确认执行 → **在终端执行**。
- **脚本参数**：`[groupName, fullHotfixName]`。

### 4.7 ZeroGit: Finish Hotfix

- **脚本**：`FinishHotfix.sh`
- **流程**：与 Finish Release 类似（Maintainer 确认、运维上线确认 → 选 Git 根 → gitCheck → 选择要结束的 hotfix 分支 → 确认 → **同步执行** → 解析剩余分支并可选提示）。失败时提示「FinishHotfix 失败，请通过日志查看具体原因」。
- **脚本参数**：`[groupName, selectedHotfixBranch, groupsList]`。

---

## 5. 脚本解析与执行机制（IDEA 实现）

### 5.1 脚本来源优先级

1. **项目 Git 根目录**：若存在同名脚本（如 `StartNewFeature.sh`），优先使用。
2. **远程下载**：否则从配置的 Script URL 拼接脚本名下载到 `System.getProperty("java.io.tmpdir")`（或现有 `CommandUtils.getTempFolder()`），再执行。下载失败提示 URL 不可用。
3. **缓存清理**：每次执行任意 ZeroGit 命令前，可清理临时目录下已缓存的脚本文件（gitCheck.sh 及各 `*Feature*`/`*Release*`/`*Hotfix*`.sh），确保下次可从远程重新下载（与 VS Code 行为一致）。

### 5.2 脚本与命令映射

| Action / 功能       | 脚本文件名        |
|---------------------|-------------------|
| Start New Feature   | StartNewFeature.sh |
| Finish Feature      | FinishFeature.sh   |
| Rebase Feature      | RebaseFeature.sh   |
| Start New Release   | StartNewRelease.sh |
| Finish Release      | FinishRelease.sh   |
| Start New Hotfix    | StartNewHotfix.sh  |
| Finish Hotfix       | FinishHotfix.sh    |

### 5.3 Bash 与参数

- 参数传递：与 VS Code 一致，单引号转义（单引号内单引号用 `'\''`）；或使用 `CommandLine.addArgument` 逐参数传入（避免 shell 注入）。
- Windows：使用 `ZeroGitDeploySetting.getGitHome() + "\\bin\\bash.exe"`；未配置时提示在 Settings 中配置 Git 主目录。
- Debug 开启时：执行命令带 `-x`；解析「剩余分支」等输出时过滤掉以 `+` 开头的 trace 行。

### 5.4 执行方式区分

- **在终端执行**：Start New Feature、Finish Feature、Rebase Feature、Start New Release、Start New Hotfix。使用 IDEA Terminal（如 `TerminalView`、`ShellTerminalWidget.executeCommand`）或现有「在终端中运行」逻辑，便于用户看到实时输出。
- **同步执行**：Finish Release、Finish Hotfix。使用 `DeployCmdExecuter.exec(console, workHome, command, parameters, true)` 或等价方式，捕获 stdout/stderr 并解析 `REMAINING_RELEASES:` 等。

---

## 6. gitCheck（执行前检查）

在执行任何 ZeroGit 脚本前，必须通过 **gitCheck**（执行 `gitCheck.sh` 或内联等价逻辑）：

- **Git 根**：当前工作目录为 Git 仓库根（存在 `.git`）；IDEA 可用 `CommandUtils.getRootProjectPath(modulePath)` 或 `GitUtil.getRepositoryManager(project).getRepositoryForFile(virtualFile)` 得到根路径。
- **工作区干净**：`git status --porcelain` 为空。
- **当前分支**：能解析出非 HEAD 的当前分支名。
- **上游**：当前分支已设置 upstream。
- **与远程同步**：先 `git fetch origin --prune`，再比较 `@{u}...HEAD`：不能 behind（需先 pull），不能 ahead（需先 push）。

可选（配置 **checkGitVersion** 为 true）：执行 `git version`，解析版本 ≥ 2.29.x；不满足时提示安装/升级 Git。

---

## 7. 版本与分支辅助逻辑

### 7.1 分支与 Tag 获取

- **release 分支列表**：`git fetch origin --prune` + `git for-each-ref refs/heads/release/<group>/* refs/remotes/origin/release/<group>/*`，按版本降序。
- **hotfix 分支列表**：同上，前缀 `hotfix/<group>/`。
- **本地 feature 分支**：`refs/heads/feature/<group>/*`，按数字前缀降序。
- **最新远程 tag（SemVer）**：`git ls-remote --tags --refs origin`，过滤 `v?X.Y.Z`，取最大版本。

实现方式：可调用 `DeployCmdExecuter.exec` 执行上述 git 命令并解析 stdout，或使用 `git4idea` API（若引入依赖）。

### 7.2 建议版本计算（Release/Hotfix）

取「远程最新 release 版本、最新 hotfix 版本、最新 tag 版本」三者最大值，再 patch+1；若无候选则默认 `1.0.0`。

### 7.3 冲突校验

- Start Release：输入版本不得与已有 release 重名，且不得与已有 hotfix 版本相同。
- Start Hotfix：输入版本不得与已有 hotfix 重名，且不得与已有 release 版本相同。

---

## 8. UI / 交互（IDEA 实现）

### 8.1 入口与菜单

- **主菜单**：在 **Tools** 下或独立 **Zero** / **ZeroGit** 子菜单，分组为：
  - Feature：Start New Feature、Finish Feature、Rebase Feature
  - Release：Start New Release、Finish Release
  - Hotfix：Start New Hotfix、Finish Hotfix
- **右键菜单**：Project 视图下右键（ProjectViewPopupMenu），同上 7 个 Action。
- **工具栏**：可在 ToolbarRunGroup 或现有 Zero 工具栏中增加上述 7 个 Action。

与现有 **Release**、**NewBranch**、**ChangeVersion**、**AnyTool** 可并存；ZeroGit 的 7 个 Action 使用新脚本与 groupName 流程。

### 8.2 弹窗与选择

- **输入**：Feature/Release/Hotfix 名称用 `Messages.showInputDialog` 或带校验的 `DialogWrapper`；占位符、默认值、实时校验（如 feature 的 `^\d+-\S.*$`、release/hotfix 的 SemVer）。
- **选择**：工作目录/模块用 `Module` 或 Git 根选择器；要结束的 feature/release/hotfix 分支用 `JBPopupFactory.createListPopup` 或 `Messages.showEditableChooseDialog` 等列出。
- **确认**：Modal Yes/No 或 OK/Cancel，文案与 VS Code PRD 中各命令一致。

### 8.3 状态与输出

- **进度**：执行 gitCheck 或 Finish Release/Finish Hotfix 时可用 `ProgressManager.getInstance().run(Task.Backgroundable(...))` 或状态栏文案，避免 UI 卡顿。
- **输出**：Finish Release、Finish Hotfix 的完整 stdout/stderr 输出到插件 Tool Window 的 Console（与现有 Git Deploy 控制台一致），并可在失败时自动聚焦。
- **通知**：成功用 `NotificationGroup` + INFORMATION；失败用 ERROR，并提示查看日志。

### 8.4 成功与失败文案

- 成功：如「{CommandName} executed done, please check the logs in terminal.」
- 失败：展示解析后的错误信息（含 exit code）；Finish Release/Finish Hotfix 额外提示「请通过日志查看具体失败原因」。

---

## 9. 错误处理与边界

- **执行失败**：从进程 exit code、stderr、stdout 拼出用户可读信息；debug 时 stderr 过滤 `+` 开头的行。
- **未配置 groupName**：提示并引导打开 Settings → Git Deploy Settings。
- **非 Git 仓库 / 非 feature 分支（Rebase）**：明确中文提示并中止。
- **无可用分支**：如「No local feature branch found for group "a"」「No remote release branch found for group "a"」等。

---

## 10. 与现有 IdeaDeployPlugin 的对接建议

| 现有组件               | 建议用法 |
|------------------------|----------|
| `ZeroGitDeploySetting` | 增加 GROUP_NAME_KEY、CHECK_GIT_VERSION_KEY；Script URL 与 VS Code 默认对齐（同一 GitLab raw 地址）。 |
| `CommandUtils.getRootProjectPath` | 继续用于解析 Git 根。 |
| `CommandUtils.processScript`      | 复用「项目根优先 → 远程下载 → 写临时目录」逻辑；**不再**对脚本内容做 `#cd #{project}` 替换（VS Code 脚本无此占位符）。若现有脚本有该占位符，仅对旧脚本保留；新 ZeroGit 脚本直接按路径执行即可。 |
| `DeployCmdExecuter.exec`          | 用于 gitCheck、Finish Release、Finish Hotfix 的同步执行及输出到 Console。 |
| `CmdBuilder` + Terminal 执行      | 用于其余 5 个命令的「在终端中运行」。 |
| `DeployPluginHandler`             | 可拆分为「ZeroGit 流程 Handler」与原有 Release/NewBranch/ChangeVersion/AnyTool 的 Handler；或新建 `ZeroGitFlowHandler`，仅负责 7 个命令的入参收集与脚本调用。 |
| plugin.xml                         | 新增 7 个 Action 的注册与菜单/工具栏项；保留现有 Release、NewBranch、ChangeVersion、AnyTool。 |

---

## 11. 附录：脚本文件清单（与 VS Code 一致）

| 脚本名             | 用途 |
|--------------------|------|
| gitCheck.sh        | 工作区干净、当前分支、upstream、与远程同步、可选 Git 版本检查 |
| StartNewFeature.sh | 从 develop-<group> 拉取并创建 feature 分支并 push |
| FinishFeature.sh   | 删除本地 feature 分支（前提：已在 GitLab MR 并 merge） |
| RebaseFeature.sh   | 对当前 feature 分支做 rebase |
| StartNewRelease.sh | 创建 release 分支 |
| FinishRelease.sh   | release → master、打 tag、删 release、master 合并回 develop 及未完成 release/hotfix |
| StartNewHotfix.sh  | 创建 hotfix 分支 |
| FinishHotfix.sh    | hotfix → master、打 tag、同步回 develop 等 |

以上脚本**直接复用** VscodeDeployPlugin 的 `scripts/` 目录，不修改任何脚本代码。

---

**文档版本**：1.0  
**基于**：VscodeDeployPlugin/prd.md + IdeaDeployPlugin 现有代码结构  
**用途**：JetBrains IDEA 插件（IdeaDeployPlugin）重构与 ZeroGit 功能对齐
