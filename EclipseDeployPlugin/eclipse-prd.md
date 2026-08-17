# `zerofinance-git` Eclipse 插件产品说明（当前实现）

本文档按 `EclipseDeployPlugin` 当前代码同步更新，基线为仓库当前实现（校对日期：2026-08-17，对应 Bundle 版本 `2.0.0.qualifier`）。  
旧版文档把 Eclipse 插件描述为“仍为旧版 Aeasycredit Plugin 1.1.29、待改造为 ZeroGit 的目标态”，这一说法已经过期；当前 Eclipse 插件已完成改造，与 IDEA / VS Code 对齐为同一套 ZeroGit 工具集。

---

## 1. 产品概述

- **Bundle 名**：`ZeroGit Eclipse Plugin`
- **Bundle Symbolic Name**：`com.zerofinance.zerogit.eclipse`
- **定位**：在 Eclipse 中统一执行 ZeroGit / Git Flow / Maven / GitLab CI 辅助流程，业务行为、参数校验、脚本调用规则与 IDEA / VS Code 版本保持一致
- **技术基线**：
  - JavaSE-1.8
  - Eclipse 插件（`Require-Bundle`：`org.eclipse.ui`、`org.eclipse.core.resources`、`org.eclipse.core.commands`、`org.eclipse.ui.console`、`org.yaml.snakeyaml` 等）
  - 复用与 IDEA / VS Code 相同的 ZeroGit Shell 脚本体系

当前能力由以下 **14 个入口**构成：

1. `Generate Commit Message`
2. `AI Code Review`
3. `Update Skills`
4. `Maven Change`
5. `Start New Feature`
6. `Finish Feature`
7. `Rebase Feature`
8. `Merge Request`
9. `Start New Release`
10. `Finish Release`
11. `Start New Hotfix`
12. `Finish Hotfix`
13. `Run CI Command`
14. `GitFlow Guideline`

---

## 2. 运行约束

### 2.1 脚本复用原则

- 与 IDEA / VS Code 复用同一套 ZeroGit Shell 脚本。
- 运行时优先查找“**当前 Git 仓库根目录**下的同名脚本”。
- 若仓库根不存在同名脚本，则从配置的 Script URL 下载到系统临时目录（`<java.io.tmpdir>/zerogit-cache`）执行。
- 每次执行前都会清理 ZeroGit 临时脚本缓存。
- `ScriptResolver` 负责本地覆盖判定与远程脚本下载；`ZeroGitCommandRunner` 负责脚本执行与退出码判定。

### 2.2 脚本钩子

共享脚本支持仓库根目录中的：

- `Pre_<ScriptName>.sh`
- `Post_<ScriptName>.sh`

Eclipse 插件不破坏该扩展机制，与 IDEA / VS Code 在脚本层面一致。

### 2.3 平台约束

- macOS / Linux：直接使用 `bash`
- Windows：读取配置项 `Git Home`
  - 未配置有效 Git Bash 时，执行会被中断并引导用户进入设置页
  - 最终执行路径为 `<Git Home>\bin\bash.exe`
- `Debug=true` 时，脚本执行带 `bash -x`

---

## 3. 配置项

设置页来自 `ZeroGitPreferencePage`，展示名为 **Git Deploy Settings**，配置 Key 与 IDEA 版完全一致：

| 配置项 | 存储 Key | 默认值 | 说明 |
|---|---|---|---|
| Git Home | `gitDeployPluginGitHomeKey` | 空 | Windows 下必填，指向 Git 安装目录 |
| Script URL | `gitDeployPluginScriptURLKey` | `https://gitlab.zerofinance.net/dave.zhao/deployPlugin/-/raw/main/git-flow` | 远程脚本根地址 |
| Debug | `gitDeployPluginDebugKey` | `false` | 开启后 Bash 命令带 `-x` |
| Group Names | `gitDeployPluginGroupNamesKey` | `a b c` | 允许选择的分组列表，空格分隔 |
| Group Default Name | `gitDeployPluginGroupNameKey` | 首个有效分组 | 仅用于默认选项；执行时仍会弹窗选择 |
| Git MR Assignees | `gitDeployPluginGitMrAssigneesKey` | `faker.zhou justin.wang conan.chen rain.he` | Merge Request assignee 候选 |
| Check Git Version | `gitDeployPluginCheckGitVersionKey` | `false` | 是否要求 Git 版本 `>= 2.29` |

当前实现要点：

- `ZeroGitSettings` 统一负责读取、默认值兜底（`groupNames` 为空时回退 `a b c`）
- `groupName` 不再是静态必填后直接使用，而是每次执行命令时都从 `groupNames` 中再次弹窗选择

---

## 4. 核心概念

### 4.1 分组

- 分支前缀：
  - `feature/<group>/`
  - `release/<group>/`
  - `hotfix/<group>/`
- 开发分支：
  - `develop-<group>`
- 当前实现中，除 `Finish Release` / `Finish Hotfix` / `Generate Commit Message` / `AI Code Review` / `Update Skills` 外，命令执行前都会先选择 group

### 4.2 版本规则

| 类型 | 规则 | 示例 |
|---|---|---|
| Feature | `feature/<group>/<number>-<desc>` | `feature/a/001-login` |
| Release | `release/<group>/X.Y.Z` | `release/a/1.2.0` |
| Hotfix | `hotfix/<group>/X.Y.Z` | `hotfix/a/1.2.1` |

### 4.3 版本建议逻辑

- `Start New Release`
  - 取“**全部远程 release/hotfix 带日期 tag** + 所有 group 的远程 release/hotfix 分支”中的最大 SemVer（不再只看最新标签，避免版本跳跃）
  - 按 **minor + 1，patch 清零** 生成建议版本
  - 再避开当前 group 已有冲突版本
  - 输入弹窗同时展示“最新 tag / 最新 release / 最新 hotfix”作为参考
- `Start New Hotfix`
  - 必须先找到最新生产 tag
  - 取“最新生产 tag + 所有 group 的远程 release/hotfix 分支”中的最大 SemVer
  - 按 **patch + 1** 生成建议版本
  - 再避开当前 group 已有冲突版本

可识别的 tag 形态：

- `vX.Y.Z`
- `release/<group>/X.Y.Z-YYYYMMDDHHmm`
- `hotfix/<group>/X.Y.Z-YYYYMMDDHHmm`

---

## 5. 功能清单

### 5.1 Git / AI / CI 辅助功能

| Command | 脚本/来源 | 当前行为 |
|---|---|---|
| Generate Commit Message | `GenCommitMessage.sh` | 只针对已暂存变更运行；依赖本机 `codex`；默认模型 `gpt-5.4`；只生成 message，不自动 commit |
| AI Code Review | `AiCodeReview.sh` | 支持输入提交范围（单个 commit / commit 范围），留空则只评审已暂存变更；依赖本机 `codex`；要求 `code-review-expert` skill 已安装 |
| Update Skills | `GetSkills.sh` + `UpdateSkills.sh` | 先执行 `GetSkills.sh` 列出全局/项目级 skill 及其 update/delete 动作；弹窗展示（默认全选）；再执行 `UpdateSkills.sh` 统一更新或删除 |
| Maven Change | `MavenChange.sh` | 从当前选择目录向上定位最近的有效 Maven 项目；选择 `release/snapshot`、输入版本；`release` 会校验 Nexus2 并执行 `mvn deploy` |
| Merge Request | `GitMergeRequest.sh` | 通过 GitLab push options 创建 MR；默认目标分支 `develop-<group>`；assignee 可从候选中选择或手填 |
| Run CI Command | 直接解析 `.gitlab-ci.yml` | 从 `BASE_EXEC_CMD` 候选中选择一条命令，在 Console 中执行 |
| GitFlow Guideline | Feishu 链接 | 浏览器打开团队 GitFlow 指南 |

### 5.2 ZeroGit Flow 功能

| Command | 脚本 | 执行方式 | 当前行为摘要 |
|---|---|---|---|
| Start New Feature | `StartNewFeature.sh` | 后台 Job + Console | 选择 group，输入 `feature/<group>/001-desc`，通过 `gitCheck` 后执行 |
| Finish Feature | `FinishFeature.sh` | 后台 Job + Console | 先确认已 MR 到 `develop-<group>`，再从本地 feature 列表中选择分支 |
| Rebase Feature | `RebaseFeature.sh` | 后台 Job + Console | 不跑 `gitCheck`；要求当前分支必须为 `feature/<group>/...` |
| Start New Release | `StartNewRelease.sh` | 后台 Job + Console | 先确认提测时机；若发现依赖里有 `-SNAPSHOT` 会二次确认；版本建议基于全部远程 tag / release / hotfix 推导 |
| Finish Release | `FinishRelease.sh` | 后台 Job + Console | 不先选 group；先确认 Maintainer 权限与上线完成，再选择 release 分支；执行后解析剩余 release/hotfix 分支提示 |
| Start New Hotfix | `StartNewHotfix.sh` | 后台 Job + Console | 先确认主干回合情况；若发现依赖里有 `-SNAPSHOT` 会二次确认；必须基于最新生产 tag 创建 |
| Finish Hotfix | `FinishRelease.sh` | 后台 Job + Console | 不先选 group；流程同 Finish Release，但目标分支为 hotfix |

---

## 6. 各命令的关键细节

### 6.1 Start New Feature

- 输入值必须以 `feature/<group>/` 开头
- 后缀必须满足 `^\d+-\S.*$`
- 脚本参数：`[groupName, fullFeatureName]`

### 6.2 Finish Feature

- 先确认“是否已在 GitLab 中 MR 到 `develop-<group>` 并完成 Merge”
- 只从本地 `feature/<group>/` 分支中选目标
- 分支按数字前缀降序排列
- 脚本参数：`[groupName, selectedFeatureBranch]`

### 6.3 Rebase Feature

- 当前分支必须匹配 `feature/<group>/`
- 脚本参数：`[groupName, currentBranch]`
- 这是当前实现中少数不跑 `gitCheck` 的命令

### 6.4 Merge Request

- 通过 `EditableSelectionDialog` 选择或手动输入 assignee
- assignee 不能为空
- 脚本从最新一次非 `MR-` 前缀提交中提取标题作为 MR title
- 若当前分支没有待推送提交，脚本会自动创建空提交触发 MR
- 默认目标分支 `develop-<group>`
- 脚本参数：`[groupName, assignee]`

### 6.5 Maven Change

- 从当前选择目录向上定位最近的有效 Maven 项目（含 `pom.xml`），而不是简单使用整个 Git 仓库根
- `release` / `snapshot` 两种模式均通过对话框选择
- `snapshot`：
  - 若当前版本可解析，则建议 `patch + 1` 后追加 `-SNAPSHOT`
- `release`：
  - 当前版本必须为 `-SNAPSHOT` 或 `-RCN`
  - `-SNAPSHOT` 建议转成 `-RC1`
  - `-RCN` 建议转成 `-RC(N+1)`
  - 脚本会调用 Nexus2 检查版本是否已存在
- 脚本参数：`[groupName, mavenVersion]`

### 6.6 AI Code Review

- 支持输入提交范围：
  - 单个 commit（如 `HEAD`）：只评审该提交
  - commit 范围（如 `HEAD~3`）：评审该范围内的提交
  - 留空：只评审已暂存变更，未暂存变更时提示先执行 `git add`
- 依赖本机 `codex` 命令与已安装的 `code-review-expert` skill
- 不选 group、不跑 `gitCheck`
- 脚本参数：`[commitRange]`（留空时不传参）

### 6.7 Update Skills

- 先执行 `GetSkills.sh` 拉取可更新/删除的 skill 列表
- `GetSkills.sh` 输出为严格协议，每行：`<action> <scope> <skill...>`
  - `action`：`update` / `delete`
  - `scope`：`global` / `project`
  - 同一 skill 出现冲突动作、非法名称或非法 action/scope 时直接报错
- 通过 `CheckboxSelectionDialog` 展示全部 skill（默认全选），取消则不执行
- 确认后执行 `UpdateSkills.sh`，参数按 `update → delete`、`global → project` 顺序分组
- 不选 group、不跑 `gitCheck`
- 脚本参数：由 `SkillUpdateSupport.buildArgs` 构造

### 6.8 Start New Release

- 先确认“是否已执行 FinishFeature、是否准备提测”
- 若依赖或插件版本中存在 `-SNAPSHOT`，会额外弹窗确认
- 建议版本基于“全部远程带日期 tag + 所有 group 的远程 release/hotfix 分支”计算最大 SemVer，按 **minor + 1（patch 清零）** 建议，并避开当前 group 已存在的版本
- `StartNewRelease.sh` 在 Maven 项目中会自动：
  - 创建 `release/<group>/X.Y.Z`
  - 将 `pom.xml` 改为 `X.Y.Z-RC1`
  - 自动提交 `chore: set version to X.Y.Z-RC1`
  - push 到远端并设置 upstream
- 脚本参数：`[groupName, fullReleaseName]`

### 6.9 Finish Release

- 不需要先选择 group
- 必须先确认：
  - 当前用户有 Maintainer 权限
  - 此功能仅用于处理 CICD 自动 merge 冲突
  - 运维已完成上线
- 输出写入 Eclipse Console，成功后由 `FinishReleaseOutputParser` 解析剩余分支：
  - `REMAINING_RELEASES:`
  - `Remaining release branches:`
  - `Remaining hotfix branches:`
- 脚本参数：`[selectedReleaseBranch]`

### 6.10 Start New Hotfix

- 必须先确认上线后的代码已及时回合到 `main/develop/release/hotfix`
- 若依赖或插件版本中存在 `-SNAPSHOT`，会额外弹窗确认
- 必须先找到最新生产 tag，找不到则直接中断
- 创建前会再次确认“即将基于哪个 tag 创建 hotfix”
- 脚本实际执行 `git switch -c <hotfix> <baseTag>`
- 脚本参数：`[groupName, fullHotfixName, baseTag]`

### 6.11 Finish Hotfix

- 与 Finish Release 共用 `FinishRelease.sh`
- 不需要先选择 group
- 执行方式同 Finish Release
- 脚本参数：`[selectedHotfixBranch]`

---

## 7. `gitCheck` 规则

以下命令不跑 `gitCheck`：

- `Generate Commit Message`
- `AI Code Review`
- `Update Skills`
- `Maven Change`
- `Rebase Feature`

其余 ZeroGit 主流程（Start/Finish Feature、Start/Finish Release、Start/Finish Hotfix、Merge Request）都会在后台 Job 中先执行 `gitCheck.sh`。当前检查内容：

1. 工作区必须干净
2. 当前不能处于 detached HEAD
3. 当前分支必须已配置 upstream
4. 先执行 `git fetch origin --prune`
5. 不能 behind 远端
6. 不能 ahead 远端
7. 若设置 `Check Git Version=true`，则先通过 `GitVersionChecker` 校验 Git 版本 `>= 2.29`

---

## 8. UI 入口与交互

### 8.1 菜单与入口

当前 `plugin.xml` 已注册以下入口：

- 项目 / 编辑器右键菜单（`popup:org.eclipse.ui.popup.any`）下的 `ZeroGit`
- 菜单结构：
  - `Maven`：`Maven Change`
  - `Feature`：`Start New Feature`、`Finish Feature`、`Rebase Feature`、`Merge Request`
  - `Release`：`Start New Release`、`Finish Release`
  - `Hotfix`：`Start New Hotfix`、`Finish Hotfix`
  - `GitLab CI`：`Run CI Command`
  - 分隔线
  - `AI Tools`：`Generate Commit Message`、`AI Code Review`、`Update Skills`
  - `GitFlow Guideline`

### 8.2 选择与输入

- 所有 group、branch、release、assignee 选择均使用 Eclipse 原生风格对话框（`UserInteraction` + Topmost 变体，确保焦点在 Eclipse 之上）
- 关键风险动作使用确认对话框二次确认
- 输入框提供默认建议值，不要求用户从空白开始输入

### 8.3 输出与反馈

- 所有脚本执行过程写入 Eclipse `MessageConsole`（`ZeroGit` Console）
- 成功后自动刷新项目资源并给出成功提示
- 失败时保留原始脚本输出，并给出错误摘要对话框

---

## 9. 工程结构

- `com.zerofinance.zerogit.eclipse`：插件主工程
  - `actions`：14 个命令 Handler（统一继承 `AbstractZeroGitHandler`）
  - `exec`：`ScriptResolver`、`ZeroGitCommandRunner`、`BashCommandBuilder`、`CommandRequest/CommandResult`
  - `flow`：`ZeroGitFlowService`（脚本参数构造、分支名校验）、`PomSnapshotSupport`（pom 依赖/插件 SNAPSHOT 检测）、`SkillUpdateSupport`、`FinishReleaseOutputParser`
  - `git`：`GitRepositoryService`、`GitVersionChecker`、`GitVersionSupport`、`VersionService`
  - `settings`：`ZeroGitPreferencePage`、`ZeroGitSettings`、`PreferenceConstants`
  - `ui`：`UserInteraction` 及 Topmost 对话框组件
- `com.zerofinance.zerogit.eclipse.feature`：Feature 工程
- `com.zerofinance.zerogit.eclipse.updatesite`：更新站点工程
- `com.zerofinance.zerogit.eclipse.tests`：测试工程（`SkillUpdateSupportTest`、`VersionServiceTest`）
- `scripts/`：仅包含构建辅助脚本（`check-plugin-export-packages.sh`、`run-pde-tests.sh`），不是业务脚本基线；业务脚本仍以仓库根目录同名脚本 / 远程脚本为主

---

## 10. 与 IDEA / VS Code 的一致性

- 同一仓库、同一脚本、同一输入下，Eclipse 与 IDEA / VS Code 的执行结果保持一致
- Git Flow 业务逻辑全部在共享脚本中，Eclipse 端只负责 UI、参数收集、校验与执行编排
- 配置 Key、分组模型、版本建议规则、`gitCheck` 规则、Pre/Post Hook 机制均与 IDEA 版对齐
- 旧版 `Aeasycredit Plugin` 命名、`ChangeVersion` / `New Branch` / `Release` / `Mybatis Gen` / `Code Gen` 入口与 `*.x / *.release / *.hotfix` 旧分支模型已不再保留
