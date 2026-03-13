# ZeroGit (zerofinance-git) VS Code 插件 — 产品需求文档 (PRD)

本文档基于现有 VS Code 插件（VscodeDeployPlugin）整理，用于指导 JetBrains IDEA 插件的重构与功能对齐。

---

## 约束说明：scripts 脚本复用

- **`scripts/` 目录下的所有脚本必须直接复用，不做任何修改。**
- 重构或移植到 JetBrains IDEA 时，仅允许调整「插件/IDE 侧」的配置、UI、调用方式；不得改动 `scripts/` 内任何 `.sh` 或脚本逻辑。
- 脚本的入参、输出格式、执行环境（Bash、工作目录、参数顺序）均以现有脚本为准，插件只负责按约定传参与解析输出。

---

## 1. 项目概述

### 1.1 定位

- **产品名称**：zerofinance-git（对外展示名：ZeroGit）
- **类型**：基于 Git Flow 工作流的 IDE 扩展，面向多分组（如 A/B 组）的 feature / release / hotfix 分支管理。
- **核心价值**：通过配置分组 + 远程 Shell 脚本，在 IDE 内一键完成「开 feature / 完成 feature / rebase feature / 开 release / 完成 release / 开 hotfix / 完成 hotfix」等标准化流程，减少手工敲命令与误操作。

### 1.2 技术栈（当前 VS Code 实现）

- **运行时**：Node.js，VS Code Extension API
- **依赖**：`axios`（下载脚本）、`tmp`（临时目录）
- **脚本执行**：通过集成终端执行 Bash 脚本；Windows 下依赖配置的 Git Bash 路径

---

## 2. 核心概念

### 2.1 分组（Group）

- 所有操作都**必须**先配置 `zerofinanceGit.groupName`，且只能为预定义枚举值（如 `a`、`b`）。
- 分组用于：
  - 分支前缀：`feature/<group>/`、`release/<group>/`、`hotfix/<group>/`
  - develop 分支名：`develop-<group>`（如 `develop-a`、`develop-b`）
- 未配置或配置非法时，执行任何命令会提示「请先配置 groupName 为 a 或 b」，并可跳转打开设置。

### 2.2 分支命名规范

| 类型     | 前缀格式              | 名称/版本规则              | 示例                    |
|----------|-----------------------|----------------------------|-------------------------|
| feature  | `feature/<group>/`    | 数字-描述，如 `001-login`  | `feature/a/001-login`   |
| release  | `release/<group>/`    | SemVer：`X.Y.Z`            | `release/a/1.0.0`       |
| hotfix   | `hotfix/<group>/`     | SemVer：`X.Y.Z`            | `hotfix/a/1.0.1`        |

- **develop**：`develop-<group>`，如 `develop-a`。
- **main**：主发布分支，release/hotfix 完成后合并到 main 并打 tag（如 `v1.0.0`）。

### 2.3 Git Flow 流程简述

- **Feature**：从 `develop-<group>` 拉取 `feature/<group>/xxx`，开发完成后 MR 到 `develop-<group>`，再在插件中「Finish Feature」删除本地 feature 分支。
- **Release**：从 develop 拉取 `release/<group>/X.Y.Z`，测试通过后「Finish Release」：合并到 main、打 tag、删除 release 分支、将 main 合并回各 develop 及未完成的 release/hotfix 分支。
- **Hotfix**：从 main 拉取 `hotfix/<group>/X.Y.Z`，修完后「Finish Hotfix」：合并回 main、打 tag、同步回 develop 等（与 Finish Release 类似）。

---

## 3. 配置项

| 配置键 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `zerofinanceGit.gitScriptsUrlPreference` | string | 指定 GitLab raw 地址 | 用于下载 Shell 脚本的根 URL，末尾不要带 `/`。脚本名直接拼接，如 `{url}/StartNewFeature.sh`。 |
| `zerofinanceGit.checkGitVersion` | boolean | true | 是否在执行前检查本机 Git 版本（要求 ≥ 2.29）。 |
| `zerofinanceGit.debug` | boolean | true | 是否开启调试日志（Extension Host 控制台），且脚本执行时是否带 `bash -x` 跟踪。 |
| `zerofinanceGit.groupName` | string | `""` | 必选。枚举：`""` \| `a` \| `b`（可扩展）。为空时提示选择分组。 |

---

## 4. 命令与功能清单

以下 7 个命令需在 IDEA 重构时一一对齐行为与交互。

### 4.1 ZeroGit: Start New Feature

- **命令 ID**：`extension.StartNewFeature`
- **脚本**：`StartNewFeature.sh`
- **流程概要**：
  1. 校验 `groupName` 已配置。
  2. 弹窗输入 feature 名称：占位符/默认带 `feature/<group>/`，校验规则：
     - 必须以 `feature/<group>/` 开头；
     - 后缀须匹配 `^\d+-\S.*$`（如 `001-login`）。
  3. 选择工作区目录（多根工作区时）。
  4. 执行 `gitCheck`（见第 6 节）。
  5. 解析脚本路径（本地优先，否则从配置 URL 下载）。
  6. 确认执行对话框（命令名、工作目录、脚本名、参数）。
  7. 在终端执行：`<bash> <script> <groupName> <fullFeatureName>`。
- **脚本行为**：`git checkout develop-<group>` → `git pull` → `git checkout -b <featureName>` → `git push --set-upstream origin <featureName>`。
- **参数**：`[groupName, fullFeatureName]`。

### 4.2 ZeroGit: Finish Feature

- **命令 ID**：`extension.FinishFeature`
- **脚本**：`FinishFeature.sh`
- **流程概要**：
  1. 校验 `groupName`。
  2. **确认对话框**：「是否已在 GitLab 中 MR 到 develop-<group> 并完成 Merge？继续只会删除本地 feature 分支。」→ 否则中止。
  3. 选择工作区 → `gitCheck` → 解析脚本。
  4. 从本地分支列表选择要结束的 feature 分支（仅 `feature/<group>/` 前缀，按数字前缀降序）。
  5. 再次确认执行脚本。
  6. 在终端执行脚本。
- **参数**：`[groupName, selectedFeatureBranch]`。

### 4.3 ZeroGit: Rebase Feature

- **命令 ID**：`extension.RebaseFeature`
- **脚本**：`RebaseFeature.sh`
- **流程概要**：
  1. 校验 `groupName`，选择工作区，`gitCheck`，解析脚本。
  2. 获取当前分支：若非 `feature/<group>/` 开头，报错并中止。
  3. 确认执行后，在终端执行脚本，参数为当前分支名。
- **参数**：`[groupName, currentBranch]`。

### 4.4 ZeroGit: Start New Release

- **命令 ID**：`extension.StartNewRelease`
- **脚本**：`StartNewRelease.sh`
- **流程概要**：
  1. 校验 `groupName`。
  2. **确认对话框**：「是否已执行 Finish Feature 操作？」→ 否则中止。
  3. 选择工作区 → `gitCheck` → 解析脚本。
  4. 输入 release 版本（带 `release/<group>/` 前缀）：
     - 建议版本：取「远程最新 release 版本、最新 hotfix 版本、最新 tag 版本」中最大者，patch+1；若无则 `1.0.0`。
     - 校验：SemVer `X.Y.Z`；不与已有 release 重名；不与已有 hotfix 版本冲突。
  5. 确认执行后在终端执行。
- **参数**：`[groupName, fullReleaseName]`。

### 4.5 ZeroGit: Finish Release

- **命令 ID**：`extension.FinishRelease`
- **脚本**：`FinishRelease.sh`
- **流程概要**：
  1. 校验 `groupName`。
  2. **确认 1**：合并提示「只有 Maintainer 才有权限，请确认你对该项目是否有 Maintainer 权限？

此功能仅限于解决CICD自动化merge代码时出现冲突的场景。解决完冲突后，再到项目的Pipeline里面重新执行对应的job即可。」→ 否则中止。
  3. **确认 2**：「运维是否已完成上线？」→ 否则中止。
  4. 选择工作区 → `gitCheck` → 解析脚本。
  5. 从远程（及本地）release 分支列表选择要结束的分支（按版本降序）。
  6. **确认 3**：展示命令、工作目录、脚本、参数，确认执行。
  7. **不**在终端执行，而是**同步执行**脚本（`child_process.exec`），以便解析 stdout/stderr。
  8. 从输出中解析 `REMAINING_RELEASES:` 或 `Remaining release branches:`，得到剩余 release/hotfix 分支列表；若有，再弹窗：「目前有进行中的 xxx 分支，请评估是否需要重新测试？」
  9. 失败时提示「FinishRelease 失败，请通过日志查看具体原因」。
- **参数**：`[selectedReleaseBranch]`（选中的 release 分支名，如 `release/core/1.2.3`）。脚本根据分支前缀自动识别为 release。develop 列表由脚本从远程 `develop-*` 分支自动获取。
- **脚本逻辑**：详见项目内 `FinishRelease.md`（合并到 main、打 tag、删 release、main 合并回各 develop / 未完成 release / 未完成 hotfix、输出剩余分支）。

### 4.6 ZeroGit: Start New Hotfix

- **命令 ID**：`extension.StartNewHotfix`
- **脚本**：`StartNewHotfix.sh`
- **流程概要**：
  1. 校验 `groupName`，选择工作区，`gitCheck`，解析脚本。
  2. 输入 hotfix 版本（带 `hotfix/<group>/` 前缀）：建议版本逻辑同 release；校验 SemVer、不与已有 hotfix 重名、不与 release 版本冲突。
  3. 确认执行后在终端执行。
- **参数**：`[groupName, fullHotfixName]`。

### 4.7 ZeroGit: Finish Hotfix

- **命令 ID**：`extension.FinishHotfix`
- **脚本**：`FinishRelease.sh`
- **流程概要**：与 Finish Release 类似：先弹出合并提示（Maintainer 权限确认 + 仅限处理 CICD 自动化 merge 冲突、处理后到 Pipeline 重跑对应 job），再确认运维已上线 → 选择工作区 → `gitCheck` → 选择要结束的 hotfix 分支 → 确认执行 → **同步执行**脚本 → 解析剩余分支并可选提示。参数为 `[selectedHotfixBranch]`，脚本根据分支前缀自动识别为 hotfix。失败时提示「FinishHotfix 失败，请通过日志查看具体原因」。

---

## 5. 脚本解析与执行机制

### 5.1 脚本来源优先级

1. **工作区根目录**：若存在同名脚本（如 `StartNewFeature.sh`），优先使用。
2. **远程下载**：否则从 `zerofinanceGit.gitScriptsUrlPreference` 拼接脚本名下载到系统临时目录（如 `tmp.tmpdir`），再执行。
3. 下载失败时提示无法访问的 URL 并抛出错误。

### 5.2 脚本与命令映射

| 命令 ID | 脚本文件名 |
|---------|------------|
| extension.StartNewFeature | StartNewFeature.sh |
| extension.FinishFeature | FinishFeature.sh |
| extension.RebaseFeature | RebaseFeature.sh |
| extension.StartNewRelease | StartNewRelease.sh |
| extension.FinishRelease | FinishRelease.sh |
| extension.StartNewHotfix | StartNewHotfix.sh |
| extension.FinishHotfix | FinishRelease.sh |

### 5.3 缓存清理

每次执行任意命令前，会清理临时目录下已缓存的脚本文件（包括 `gitCheck.sh` 及各 `*Flow*.sh`），确保下次可能从远程重新下载。

### 5.4 Bash 与参数

- 参数用单引号转义后拼接：`'arg'`，单引号内单引号用 `'\''` 转义。
- Windows：必须配置 `terminal.integrated.shell.windows` 为 Git Bash 路径（如 `D:\Developer\Git\bin\bash.exe`），否则报错。
- Debug 开启时，执行命令带 `-x`，便于排查；解析「剩余分支」等输出时需过滤掉以 `+` 开头的 trace 行（视为 stderr 中的噪音）。

---

## 6. gitCheck（执行前检查）

在执行任何 Git Flow 脚本前，必须通过 `gitCheck`（可由本地 `gitCheck.sh` 或远程下载）：

- **工作区**：当前目录必须是 Git 仓库根（存在 `.git`）。
- **工作区干净**：无未提交、未暂存、未跟踪的变更（`git status --porcelain` 为空）。
- **当前分支**：能解析出非 HEAD 的当前分支名。
- **上游**：当前分支已设置 upstream。
- **与远程同步**：先 `git fetch origin --prune`，再比较 `@{u}...HEAD`：不能 behind（必须先 pull），不能 ahead（必须先 push）。

可选（配置 `zerofinanceGit.checkGitVersion` 为 true）：

- 执行 `git version`，解析版本号，要求 ≥ 2.29.x；不满足时提示安装/升级 Git（可带华为云镜像链接）。

---

## 7. 版本与分支辅助逻辑

### 7.1 远程分支与 Tag 获取

- **release 分支列表**：`git fetch origin --prune` + `git for-each-ref refs/heads/release/<group>/* refs/remotes/origin/release/<group>/*`，按版本降序。
- **hotfix 分支列表**：同上，前缀改为 `hotfix/<group>/`。
- **本地 feature 分支**：`refs/heads/feature/<group>/*`，按数字前缀降序。
- **最新远程 tag（SemVer）**：`git ls-remote --tags --refs origin`，过滤 `v?X.Y.Z`，取最大版本。

### 7.2 建议版本计算（Release/Hotfix）

取以下三者最大值，再 patch+1：

- 远程最新 `release/<group>/X.Y.Z`
- 远程最新 `hotfix/<group>/X.Y.Z`
- 远程最新 tag `vX.Y.Z` 或 `X.Y.Z`

若无任何候选，默认 `1.0.0`。

### 7.3 冲突校验

- Start Release：输入版本不得与已有 release 重名，且不得与已有 hotfix 版本相同。
- Start Hotfix：输入版本不得与已有 hotfix 重名，且不得与已有 release 版本相同。

---

## 8. UI / 交互

### 8.1 入口

- **命令面板**：7 个命令均注册为 VS Code 命令（标题带 ZeroGit 前缀）。
- **菜单**：子菜单「ZeroGit」，分组为：
  - 1_feature：Start New Feature、Finish Feature、Rebase Feature
  - 2_release：Start New Release、Finish Release
  - 3_hotfix：Start New Hotfix、Finish Hotfix
- **出现位置**：编辑器右键、终端右键、视图标题（when: view == terminal）。

### 8.2 弹窗与选择

- **输入框**：Feature/Release/Hotfix 名称，带占位符、默认值、实时校验（validateInput）。
- **下拉选择**：工作区文件夹、要结束的 feature/release/hotfix 分支。
- **确认对话框**：Modal Yes/No 或单按钮 OK；文案见上文各命令。

### 8.3 状态栏与输出

- **状态栏**：执行 gitCheck 或 Finish Release/Finish Hotfix 时显示「Checking git status...」或「Finishing release, it may take a while...」，红色，结束后隐藏。
- **输出通道**：专用 Output Channel（如 `zerofinance-git-flow`），Finish Release/Finish Hotfix 的完整 stdout/stderr 写入其中并自动 show。

### 8.4 成功与失败

- 成功：`showInformationMessage`：「{CommandName} executed done, please check the logs in terminal.」
- 失败：`showErrorMessage` 展示解析后的错误信息（含 exit code）；Finish Release/Finish Hotfix 额外提示「请通过日志查看具体失败原因」。

---

## 9. 错误处理与边界

- **exec 失败**：从 `err.code`、`err.stderr`、`err.stdout`、`err.message` 拼出用户可读信息；debug 时 stderr 需过滤 `+` 开头的行。
- **未配置 group**：提示并可选打开设置页聚焦 `zerofinanceGit.groupName`。
- **非 Git 仓库 / 非 feature 分支（Rebase）**：明确中文提示并中止。
- **无可用分支**：如「No local feature branch found for group "a"」「No remote release branch found for group "a"」等。

---

## 10. 非功能需求（IDEA 重构参考）

- **配置可迁移**：IDEA 插件应提供对应配置项（脚本 URL、是否检查 Git 版本、debug、groupName 及枚举），便于从 VS Code 迁移。
- **脚本兼容**：必须直接复用本仓库 `scripts/` 目录下的脚本，不修改任何脚本代码；IDEA 仅实现「配置 → 输入/选择 → 调用脚本」这一层，调用时入参、工作目录、环境与现有约定保持一致。
- **多模块/多仓库**：当前 VS Code 以「选择工作区文件夹」确定 Git 根目录；IDEA 可映射为「选择模块/项目根」或当前打开的项目根。
- **终端 vs 同步执行**：Start Feature/Finish Feature/Rebase/Start Release/Start Hotfix 在终端执行；Finish Release、Finish Hotfix 同步执行并解析输出（剩余分支、错误信息），IDEA 需保留该差异。
- **日志**：IDEA 可提供 Run 控制台或专用 Tool Window 输出脚本 stdout/stderr，便于排查。
- **i18n**：当前插件中确认文案多为中文，IDEA 可保留中文或做国际化。

---

## 11. 附录：脚本文件清单

| 脚本名 | 用途 |
|--------|------|
| gitCheck.sh | 工作区干净、当前分支、upstream、与远程同步、可选 Git 版本检查 |
| StartNewFeature.sh | 从 develop-<group> 拉取并创建 feature 分支并 push |
| FinishFeature.sh | 删除本地 feature 分支（前提：已在 GitLab MR 并 merge） |
| RebaseFeature.sh | 对当前 feature 分支做 rebase（通常基于 develop-<group>） |
| StartNewRelease.sh | 创建 release 分支 |
| FinishRelease.sh | release/hotfix → main、打 tag、删分支、main 合并回 develop 及未完成 release/hotfix（由分支前缀区分模式） |
| StartNewHotfix.sh | 创建 hotfix 分支 |

以上脚本**直接复用**，不得修改 `scripts/` 目录内任何脚本代码；具体实现以仓库内 `scripts/` 及远程 `zerofinanceGit.gitScriptsUrlPreference` 为准，PRD 仅描述插件侧行为与约定。

---

**文档版本**：1.0  
**基于**：VscodeDeployPlugin 源码（extension.js、package.json、scripts、FinishRelease.md）整理  
**用途**：JetBrains IDEA 插件重构与功能对齐
