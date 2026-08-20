# `zerofinance-git` IntelliJ IDEA 插件产品说明（当前实现）

本文档按 `IdeaDeployPlugin` 当前代码同步更新，基线为仓库当前实现（校对日期：2026-08-17，对应插件版本 `2.0.13`）。  
旧版文档把 IDEA 插件描述成“待对齐 VS Code 的 7 个命令重构目标”，这一说法已经过期；当前 IDEA 插件已经落地为完整的 ZeroGit 工具集。

---

## 1. 产品概述

- **插件 ID**：`com.zerofinance.git`
- **插件名**：`zerofinance-git`
- **定位**：在 IntelliJ IDEA 中统一执行 ZeroGit / Git Flow / Maven / GitLab CI 辅助流程
- **技术基线**：
  - Java 11
  - Gradle Kotlin DSL
  - IntelliJ Platform `2022.1`
  - 依赖 `org.jetbrains.plugins.terminal`

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

- IDEA 插件与 VS Code 插件复用同一套 ZeroGit Shell 脚本。
- 运行时优先查找“**当前 Git 仓库根目录**下的同名脚本”。
- 若仓库根不存在同名脚本，则从配置的 Script URL 下载到系统临时目录执行。
- 每次执行前都会清理 ZeroGit 临时脚本缓存。
- IDEA 插件仓库中的 [`scripts/`](./scripts) 只是当前仓库自带脚本资产；业务仓库要覆盖脚本，覆盖点仍然是“业务仓库根目录同名脚本”。

### 2.2 Windows 约束

- Windows 环境下，`ZeroGitFlowHandler` 构造时会强制检查 `Git Home`
- 未配置时会直接弹出设置页并中断执行
- 最终执行路径为 `<Git Home>\\bin\\bash.exe`

### 2.3 脚本钩子

共享脚本支持仓库根目录中的：

- `Pre_<ScriptName>.sh`
- `Post_<ScriptName>.sh`

因此 IDEA 与 VS Code 在脚本层面具备一致的扩展点。

---

## 3. 配置项

设置页来自 `ZeroGitDeploySetting`，展示名为 **Git Deploy Settings**。

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

- `groupName` 不再是静态必填后直接使用，而是每次执行命令时都从 `groupNames` 中再次弹窗选择
- 设置页里 `groupName` 主要承担“默认高亮项”的作用

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

| Action | 脚本/来源 | 当前行为 |
|---|---|---|
| Generate Commit Message | `GenCommitMessage.sh` | 只针对已暂存变更运行；依赖本机 `codex`；默认模型 `gpt-5.4`；只生成 message，不自动 commit |
| AI Code Review | `AiCodeReview.sh` | 支持输入提交范围（单个 commit / commit 范围），留空则只评审已暂存变更；依赖本机 `codex`；要求 `code-review-expert` skill 已安装 |
| Update Skills | `GetSkills.sh` + `UpdateSkills.sh` | 先执行 `GetSkills.sh` 列出全局/项目级 skill 及其 update/delete 动作；弹窗展示（默认全选）；再执行 `UpdateSkills.sh` 统一更新或删除 |
| Maven Change | `MavenChange.sh` | 选择 Maven 子项目、选择 `release/snapshot`、输入版本；`release` 会检查 Nexus2 并执行 `mvn deploy` |
| Merge Request | `GitMergeRequest.sh` | 通过 GitLab push options 创建 MR；默认目标分支 `develop-<group>`；assignee 可从候选中选择或手填 |
| Run CI Command | 直接解析 `.gitlab-ci.yml` | 从 `BASE_EXEC_CMD` 候选中选择一条命令，在 Terminal 中执行 |
| GitFlow Guideline | Feishu 链接 | 浏览器打开团队 GitFlow 指南 |

### 5.2 ZeroGit Flow 功能

| Action | 脚本 | 执行方式 | 当前行为摘要 |
|---|---|---|---|
| Start New Feature | `StartNewFeature.sh` | IDEA Terminal | 选择 group，输入 `feature/<group>/001-desc`，通过 `gitCheck` 后执行 |
| Finish Feature | `FinishFeature.sh` | IDEA Terminal | 先确认已 MR 到 `develop-<group>`，再从本地 feature 列表中选择分支 |
| Rebase Feature | `RebaseFeature.sh` | IDEA Terminal | 不跑 `gitCheck`；要求当前分支必须为 `feature/<group>/...` |
| Start New Release | `StartNewRelease.sh` | IDEA Terminal | 先确认提测时机；若发现依赖里有 `-SNAPSHOT` 会二次确认；版本建议基于全部远程 tag / release / hotfix 推导 |
| Finish Release | `FinishRelease.sh` | 同步执行 + Tool Window Console | 不先选 group；先确认 Maintainer 权限与上线完成，再选择 release 分支；执行后解析剩余 release/hotfix 分支提示；脚本对“标签已存在”返回失败状态 |
| Start New Hotfix | `StartNewHotfix.sh` | IDEA Terminal | 先确认主干回合情况；若发现依赖里有 `-SNAPSHOT` 会二次确认；必须基于最新生产 tag 创建 |
| Finish Hotfix | `FinishRelease.sh` | 同步执行 + Tool Window Console | 不先选 group；流程同 Finish Release，但目标分支为 hotfix |

---

## 6. 各命令的关键细节

### 6.1 Start New Feature

- 输入值必须以 `feature/<group>/` 开头
- 后缀必须满足 `^\\d+-\\S.*$`
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

- 通过 `Messages.showEditableChooseDialog` 选择或手动输入 assignee
- assignee 不能为空
- 脚本从最新一次非 `MR-` 前缀提交中提取标题作为 MR title
- 若当前分支没有待推送提交，脚本会自动创建空提交触发 MR
- 默认目标分支 `develop-<group>`
- 脚本参数：`[groupName, assignee]`

### 6.5 Maven Change

- 插件会从当前路径向上定位最近的有效 Maven 项目，而不是简单使用整个 Git 仓库根
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

- 支持输入提交范围（2.0.9 起）：
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
- 弹窗以 CheckBox 展示全部 skill（默认全选），取消则不执行
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
- 执行方式不是 Terminal，而是 `DeployCmdExecuter.exec(console, ...)`
- 输出会写入插件 Tool Window Console，并解析剩余 release/hotfix 分支进行后续提醒
- 脚本对“标签已存在”场景会返回失败状态，插件按失败处理
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

IDEA 插件中，以下命令不会先跑 `gitCheck`：

- `Generate Commit Message`
- `AI Code Review`
- `Update Skills`
- `Maven Change`
- `Rebase Feature`

其余 ZeroGit 主流程都会先执行 `gitCheck.sh`。当前检查内容：

1. 工作区必须干净
2. 当前不能处于 detached HEAD
3. 当前分支必须已配置 upstream
4. 先执行 `git fetch origin --prune`
5. 不能 behind 远端
6. 不能 ahead 远端
7. 若设置 `Check Git Version=true`，则 Git 版本必须 `>= 2.29`

---

## 8. UI 入口

当前 `plugin.xml` 已注册以下入口：

- 主菜单 `VcsGroups` 下的 `ZeroGit`
- `ProjectViewPopupMenu`
- `EditorPopupMenu`
- `ToolbarRunGroup`

菜单结构包含：

- `ZeroGit`
- `Maven`
- `Feature`
- `Release`
- `Hotfix`
- `GitLab CI`
- `AI Tools`

附加说明：

- `Feature` 子菜单包含 `Merge Request`
- `AI Tools` 子菜单包含 `Generate Commit Message`、`AI Code Review`、`Update Skills`（主菜单、右键菜单、Toolbar 三处一致）
- Toolbar 上除 `Run CI Command` 外，已挂出大部分常用入口
- Finish Release / Finish Hotfix 的日志输出依赖插件 Tool Window `GitDeployPlugin`

---

## 9. 与旧版文档相比的关键更新

本次同步（2.0.7 → 2.0.13）修正了以下过期信息：

1. IDEA 插件已是 **14 个功能入口**（新增 `Update Skills`），不是“13 个”，更不是“待实现的 7 命令对齐项目”
2. `AI Code Review` 支持指定提交范围（单提交 / commit 范围 / 留空=已暂存变更）
3. 新增 `Update Skills`：`GetSkills.sh` 严格协议解析 + 默认全选 + `UpdateSkills.sh` 统一 update/delete
4. 菜单新增 `AI Tools` 子菜单，AI 相关操作统一收纳到底部
5. `Start New Release` 建议版本改为基于“全部远程 tag + 远程 release/hotfix 分支”计算，修复仅用最新标签导致的版本跳跃
6. `FinishRelease.sh` 对“标签已存在”返回失败状态
7. `groupNames` / `groupName` 动态配置与执行前再次选择、`Git MR Assignees`、`Check Git Version`、`Run CI Command` 等既有能力保持不变
