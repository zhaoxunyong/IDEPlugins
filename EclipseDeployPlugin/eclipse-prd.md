# `zerofinance-git` Eclipse 插件改造 PRD（目标对齐 IDEA 版）

本文档基于 [`IdeaDeployPlugin/idea-prd.md`](../IdeaDeployPlugin/idea-prd.md) 当前基线（校对日期：2026-06-04，对应插件版本 `2.0.6`）与现有 `EclipseDeployPlugin` 代码现状整理，定义 Eclipse 插件的目标态需求。  
当前 Eclipse 插件仍是旧版 `Aeasycredit Plugin`（Bundle-Version `1.1.29`），仅提供 `ChangeVersion`、`New Branch`、`Release`、`Mybatis Gen`、`Code Gen` 等老入口；本文档目标是将 Eclipse 插件改造为与 IDEA 插件一致的 ZeroGit 工具集，而不是描述当前已实现状态。

---

## 1. 改造目标

- **产品目标**：将 Eclipse 插件升级为与 IDEA 插件一致的 `zerofinance-git` 工具集。
- **能力目标**：对齐 IDEA 当前 **13 个入口**的功能范围、流程约束、脚本调用规则与配置模型。
- **交互目标**：保留 Eclipse 原生交互习惯，但业务行为、参数校验、脚本命名、提示文案、异常处理结果需与 IDEA 版保持一致。
- **技术目标**：复用与 IDEA / VS Code 相同的 ZeroGit Shell 脚本体系，避免在 Eclipse 端重新实现 Git Flow 业务逻辑。

目标态入口如下：

1. `Generate Commit Message`
2. `AI Code Review`
3. `Maven Change`
4. `Start New Feature`
5. `Finish Feature`
6. `Rebase Feature`
7. `Merge Request`
8. `Start New Release`
9. `Finish Release`
10. `Start New Hotfix`
11. `Finish Hotfix`
12. `Run CI Command`
13. `GitFlow Guideline`

---

## 2. 现状与差距

### 2.1 当前 Eclipse 插件现状

- 插件仍使用旧命名：
  - 菜单名：`Aeasycredit Plugin`
  - Bundle Symbolic Name：`com.aeasycredit.DeployPlugin`
  - Java 运行基线：`JavaSE-1.8`
- 当前主要入口仅有：
  - `ChangeVersion`
  - `New Branch`
  - `Release`
  - `Mybatis Gen`
  - `Code Gen`
- 当前脚本与流程仍围绕旧分支模型：
  - `*.x`
  - `*.release`
  - `*.hotfix`
- 当前配置项缺少：
  - `groupNames`
  - `groupName`
  - `gitMrAssignees`
  - `checkGitVersion`
- 当前行为与 IDEA 不一致：
  - 仍保留 `Mybatis Gen` / `Code Gen` 等历史能力
  - 不支持 Commit Message / AI Review / Merge Request / CI Command
  - Release / Hotfix 版本规则与 IDEA 当前 SemVer + group 规则不一致

### 2.2 改造后的目标差距闭环

本次改造后，Eclipse 插件需要完成以下收敛：

- 从“老 DeployPlugin”收敛为“ZeroGit Eclipse 插件”
- 从“旧版分支版本模型”收敛为“group + feature/release/hotfix”模型
- 从“少量脚本入口”收敛为“13 个完整入口”
- 从“Eclipse 自带旧脚本”收敛为“与 IDEA / VS Code 共用同一套脚本基线”
- 从“分散且历史包袱较多的菜单结构”收敛为“以 ZeroGit 为中心的统一菜单结构”

---

## 3. 产品范围

### 3.1 本期范围

- 重构 Eclipse 插件菜单、命令、处理器、配置页与执行器
- 对齐 IDEA 插件的 13 个功能入口
- 对齐脚本下载、缓存清理、本地覆盖、Pre/Post Hook 机制
- 对齐 group 选择、版本建议、Release/Hotfix 流程、MR 流程、AI 辅助流程
- 对齐 Windows Git Bash 支持与 debug 行为
- 对齐成功/失败提示、Console 输出与任务执行模型

### 3.2 不在本期范围

- 不新增 IDEA 当前也没有的新业务能力
- 不继续扩展 `Mybatis Gen`、`Code Gen` 等历史专属功能
- 不重写 ZeroGit Shell 业务逻辑；如需变更，应以共享脚本为主
- 不要求 Eclipse 在 UI 结构上 100% 复制 IDEA，只要求功能与流程一致

### 3.3 历史能力处理策略

- `Mybatis Gen`、`Code Gen` 不再作为目标态主能力的一部分
- `ChangeVersion` / `New Branch` / `Release` 旧入口不再按当前语义保留
- 如需兼容历史用户，可在过渡版本中保留旧入口，但必须明确标记为 `Legacy`，且默认不出现在主菜单

---

## 4. 运行约束

### 4.1 脚本复用原则

- Eclipse 插件必须与 IDEA / VS Code 复用同一套 ZeroGit Shell 脚本。
- 运行时优先查找“**当前 Git 仓库根目录**下的同名脚本”。
- 若仓库根不存在同名脚本，则从配置的 Script URL 下载到系统临时目录执行。
- 每次执行前必须清理 ZeroGit 脚本缓存，避免旧脚本残留导致行为漂移。
- Eclipse 插件仓库中的 `scripts/` 仅作为开发基线资产，不作为业务仓库覆盖点。

### 4.2 脚本钩子

共享脚本必须继续支持仓库根目录中的：

- `Pre_<ScriptName>.sh`
- `Post_<ScriptName>.sh`

Eclipse 插件不得破坏该扩展机制。

### 4.3 平台约束

- macOS / Linux：直接使用 `bash`
- Windows：
  - 必须支持配置 Git 安装目录或 `bash.exe` 路径
  - 未配置有效 Git Bash 时，直接引导用户进入设置页并中断执行
  - `debug=true` 时，脚本执行需带 `-x`

### 4.4 执行体验方案

推荐方案：**统一使用 Eclipse Job + Console 流式输出执行命令**。

原因：

- Eclipse 默认没有像 IDEA 那样稳定的内置 Terminal 依赖，Console 方案更可控
- 可以统一处理 stdout/stderr、成功失败提示、任务取消与资源刷新
- 更利于实现 `Finish Release` / `Finish Hotfix` 这类需要解析输出的命令

不推荐作为主方案：

- 依赖第三方 Terminal 插件
- 调起外部系统终端

---

## 5. 配置项

目标态 Eclipse 配置页需与 IDEA 配置语义保持一致，建议统一展示为 **Git Deploy Settings**。

| 配置项 | 建议存储 Key | 默认值 | 说明 |
|---|---|---|---|
| Git Home / Git Bash | `gitDeployPluginGitHomeKey` | 空 | Windows 下必填，可填 Git 目录或 `bash.exe` |
| Script URL | `gitDeployPluginScriptURLKey` | `https://gitlab.zerofinance.net/dave.zhao/deployPlugin/-/raw/main/git-flow` | 远程脚本根地址 |
| Debug | `gitDeployPluginDebugKey` | `false` | 开启后 Bash 命令带 `-x` |
| Group Names | `gitDeployPluginGroupNamesKey` | `a b c` | 可选 group 列表，空格分隔 |
| Group Default Name | `gitDeployPluginGroupNameKey` | 首个有效 group | 作为默认高亮项，不代表执行时跳过选择 |
| Git MR Assignees | `gitDeployPluginGitMrAssigneesKey` | `faker.zhou justin.wang conan.chen rain.he` | MR assignee 候选 |
| Check Git Version | `gitDeployPluginCheckGitVersionKey` | `false` | 是否要求 Git 版本 `>= 2.29` |

配置行为要求：

- `groupName` 只作为默认值，不是固定执行值
- 每次需要 group 的命令执行前，仍需从 `groupNames` 中弹窗选择
- 旧配置项 `gitUserPreference` / `gitPwdPreference` 不再作为目标态主配置保留

---

## 6. 核心概念

### 6.1 分组

- 分支前缀：
  - `feature/<group>/`
  - `release/<group>/`
  - `hotfix/<group>/`
- 开发分支：
  - `develop-<group>`
- 除 `Finish Release` / `Finish Hotfix` / `Generate Commit Message` / `AI Code Review` 外，命令执行前都需先选择 group

### 6.2 版本规则

| 类型 | 规则 | 示例 |
|---|---|---|
| Feature | `feature/<group>/<number>-<desc>` | `feature/a/001-login` |
| Release | `release/<group>/X.Y.Z` | `release/a/1.2.0` |
| Hotfix | `hotfix/<group>/X.Y.Z` | `hotfix/a/1.2.1` |

### 6.3 版本建议逻辑

- `Start New Release`
  - 从“最新远程 tag + 所有 group 的远程 release/hotfix 分支”中找最大 SemVer
  - 在此基础上执行 **minor + 1，patch 清零**
  - 再避开当前 group 已存在的版本冲突
- `Start New Hotfix`
  - 必须先找到最新生产 tag
  - 从“最新生产 tag + 所有 group 的远程 release/hotfix 分支”中找最大 SemVer
  - 在此基础上执行 **patch + 1**
  - 再避开当前 group 已存在的版本冲突

可识别 tag 形态：

- `vX.Y.Z`
- `release/<group>/X.Y.Z-YYYYMMDDHHmm`
- `hotfix/<group>/X.Y.Z-YYYYMMDDHHmm`

---

## 7. 功能清单

### 7.1 Git / AI / CI 辅助功能

| Action | 脚本/来源 | 目标行为 |
|---|---|---|
| Generate Commit Message | `GenCommitMessage.sh` | 只针对已暂存变更运行；依赖本机 `codex`；默认模型 `gpt-5.4`；只生成 message，不自动 commit |
| AI Code Review | `AiCodeReview.sh` | 只针对已暂存变更运行；依赖本机 `codex`；要求已安装 `code-review-expert` skill |
| Maven Change | `MavenChange.sh` | 选择 Maven 子项目、选择 `release/snapshot`、输入版本；`release` 需检查 Nexus2 并执行 `mvn deploy` |
| Merge Request | `GitMergeRequest.sh` | 通过 GitLab push options 创建 MR；默认目标分支 `develop-<group>`；assignee 可选择或手填 |
| Run CI Command | 直接解析 `.gitlab-ci.yml` | 从 `BASE_EXEC_CMD` 候选中选择一条命令并执行 |
| GitFlow Guideline | Feishu 链接 | 打开团队 GitFlow 指南 |

### 7.2 ZeroGit Flow 功能

| Action | 脚本 | 目标行为摘要 |
|---|---|---|
| Start New Feature | `StartNewFeature.sh` | 选择 group，输入 `feature/<group>/001-desc`，通过 `gitCheck` 后执行 |
| Finish Feature | `FinishFeature.sh` | 先确认已 MR 到 `develop-<group>`，再从本地 feature 列表中选择分支 |
| Rebase Feature | `RebaseFeature.sh` | 不跑 `gitCheck`；要求当前分支必须为 `feature/<group>/...` |
| Start New Release | `StartNewRelease.sh` | 先确认提测时机；若发现依赖里有 `-SNAPSHOT` 会二次确认；版本按最新 tag/release/hotfix 推导 |
| Finish Release | `FinishRelease.sh` | 不先选 group；先确认 Maintainer 权限与上线完成，再选择 release 分支；执行后解析剩余 release/hotfix 分支提示 |
| Start New Hotfix | `StartNewHotfix.sh` | 先确认主干回合情况；若发现依赖里有 `-SNAPSHOT` 会二次确认；必须基于最新生产 tag 创建 |
| Finish Hotfix | `FinishRelease.sh` | 不先选 group；流程同 Finish Release，但目标分支为 hotfix |

---

## 8. 各命令关键需求

### 8.1 Start New Feature

- 输入值必须以 `feature/<group>/` 开头
- 后缀必须满足 `^\d+-\S.*$`
- 脚本参数：`[groupName, fullFeatureName]`

### 8.2 Finish Feature

- 先确认“是否已在 GitLab 中 MR 到 `develop-<group>` 并完成 Merge”
- 只从本地 `feature/<group>/` 分支中选目标
- 分支按数字前缀降序排列
- 脚本参数：`[groupName, selectedFeatureBranch]`

### 8.3 Rebase Feature

- 当前分支必须匹配 `feature/<group>/`
- 脚本参数：`[groupName, currentBranch]`
- 这是少数不跑 `gitCheck` 的命令

### 8.4 Merge Request

- assignee 不能为空
- 默认目标分支 `develop-<group>`
- 脚本从最新一次非 `MR-` 前缀提交中提取标题作为 MR title
- 若当前分支没有待推送提交，脚本会自动创建空提交触发 MR
- 脚本参数：`[groupName, assignee]`

### 8.5 Maven Change

- 插件需要从当前路径向上定位最近的有效 Maven 项目，而不是简单使用整个 Git 仓库根
- `release` / `snapshot` 两种模式均通过对话框选择
- `snapshot`：
  - 若当前版本可解析，则建议 `patch + 1` 后追加 `-SNAPSHOT`
- `release`：
  - 当前版本必须为 `-SNAPSHOT` 或 `-RCN`
  - `-SNAPSHOT` 建议转成 `-RC1`
  - `-RCN` 建议转成 `-RC(N+1)`
  - 需先校验 Nexus2 中是否已存在该版本
- 脚本参数：`[groupName, mavenVersion]`

### 8.6 Start New Release

- 先确认“是否已执行 FinishFeature、是否准备提测”
- 若依赖或插件版本中存在 `-SNAPSHOT`，需额外弹窗确认
- `StartNewRelease.sh` 在 Maven 项目中需自动：
  - 创建 `release/<group>/X.Y.Z`
  - 将 `pom.xml` 改为 `X.Y.Z-RC1`
  - 自动提交 `chore: set version to X.Y.Z-RC1`
  - push 到远端并设置 upstream
- 脚本参数：`[groupName, fullReleaseName]`

### 8.7 Finish Release

- 不需要先选择 group
- 必须先确认：
  - 当前用户有 Maintainer 权限
  - 此功能仅用于处理 CICD 自动 merge 冲突
  - 运维已完成上线
- 命令输出需要进入 Eclipse Console，并支持解析剩余 release/hotfix 分支提示
- 脚本参数：`[selectedReleaseBranch]`

### 8.8 Start New Hotfix

- 必须先确认上线后的代码已及时回合到 `main/develop/release/hotfix`
- 若依赖或插件版本中存在 `-SNAPSHOT`，需额外弹窗确认
- 必须先找到最新生产 tag，找不到则直接中断
- 创建前需再次确认“即将基于哪个 tag 创建 hotfix”
- 脚本实际执行 `git switch -c <hotfix> <baseTag>`
- 脚本参数：`[groupName, fullHotfixName, baseTag]`

### 8.9 Finish Hotfix

- 与 Finish Release 共用 `FinishRelease.sh`
- 不需要先选择 group
- 执行方式与 Finish Release 一致
- 脚本参数：`[selectedHotfixBranch]`

---

## 9. `gitCheck` 规则

除以下命令外，执行前都需要通过 `gitCheck.sh`：

- `Generate Commit Message`
- `AI Code Review`
- `Maven Change`
- `Rebase Feature`

`gitCheck.sh` 目标检查项需与 IDEA 保持一致：

1. 工作区必须干净，不能有未提交、未暂存、未跟踪文件
2. 当前必须处于正常分支，不能是 detached HEAD
3. 当前分支必须已经配置 upstream
4. 当前分支与远端状态必须可继续执行，不允许明显 out-of-date

---

## 10. Eclipse 侧交互要求

### 10.1 菜单与入口

- 在项目右键菜单、主菜单中提供统一的 `ZeroGit` 分组
- 历史 `Aeasycredit Plugin` 命名不再作为目标态对外展示名
- Toolbar 入口可选保留，但不得成为唯一入口

### 10.2 选择与输入

- 所有 group、branch、release、assignee 选择均使用 Eclipse 原生选择对话框
- 所有关键风险动作必须使用确认对话框二次确认
- 输入框必须提供默认建议值，不要求用户从空白开始输入

### 10.3 输出与反馈

- 所有脚本执行过程必须在 Eclipse Console 中可见
- 成功后需给出明确成功提示，并刷新项目资源
- 失败时需保留原始脚本输出，并给出可理解的错误摘要

---

## 11. 非功能要求

- **一致性**：同一仓库、同一脚本、同一输入下，Eclipse 与 IDEA 的执行结果必须一致
- **可维护性**：Git Flow 业务逻辑尽量在共享脚本中，Eclipse 端只做 UI、参数、校验、执行编排
- **兼容性**：至少支持当前 Eclipse 插件已有的常见使用环境，包含 Windows、macOS、Linux
- **可扩展性**：后续共享脚本新增命令时，Eclipse 端应能按统一执行框架接入

---

## 12. 验收标准

### 12.1 功能验收

- Eclipse 插件具备与 IDEA 一致的 13 个入口
- 每个入口的参数收集、校验规则、脚本参数顺序与 IDEA 保持一致
- group、版本建议、MR assignee、CI 命令解析规则与 IDEA 保持一致
- 旧版 `*.x / *.release / *.hotfix` 专属流程不再作为主流程暴露

### 12.2 运行验收

- 仓库根脚本覆盖机制可用
- 远程脚本下载与本地缓存清理可用
- Pre/Post Hook 可用
- Windows Git Bash 配置可用
- Console 输出、错误提示、项目刷新可用

### 12.3 体验验收

- 用户可以从 Eclipse 中独立完成与 IDEA 相同的 ZeroGit 日常操作
- 不需要再依赖旧版 Eclipse 专属分支模型
- 迁移后团队文档可以将 Eclipse、IDEA、VS Code 统一描述为同一套 ZeroGit 规范

---

## 13. 实施建议

- 第一阶段先完成执行框架、配置项、菜单骨架和 13 个命令占位
- 第二阶段完成与共享脚本的逐项对接及参数一致性校验
- 第三阶段清理旧入口、旧命名和旧版分支模型遗留逻辑

推荐以“**先对齐能力模型，再清理历史兼容层**”为实施顺序，避免 Eclipse 端在中间态同时维护两套 Git Flow 语义。
