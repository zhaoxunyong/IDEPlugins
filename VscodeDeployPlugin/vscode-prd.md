# `zerofinance-git` VS Code 插件产品说明（当前实现）

本文档按 `VscodeDeployPlugin` 当前代码与脚本能力同步更新，基线为仓库当前实现（校对日期：2026-06-04，对应插件版本 `2.0.6`）。  
旧版文档中“仅 7 个 Git Flow 命令、以 IDEA 重构为目标”的表述已不再准确；当前 VS Code 插件已经是完整的 ZeroGit 工具集。

---

## 1. 产品概述

- **插件名**：`zerofinance-git`
- **展示名**：`ZeroGit`
- **定位**：在 VS Code 中统一执行 ZeroGit / Git Flow / Maven / GitLab CI 辅助流程。
- **核心原则**：插件本身负责配置、校验、分支/版本选择、脚本下载与执行；Git 流程逻辑尽量落在 Shell 脚本中，保持跨 IDE 一致。

当前版本的能力不是 7 个命令，而是以下 **13 个入口**：

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

## 2. 运行约束

### 2.1 脚本复用原则

- 运行时优先查找“**当前 Git 仓库根目录**下的同名脚本”，例如 `StartNewFeature.sh`。
- 若仓库根不存在同名脚本，则从配置的 `gitScriptsUrlPreference` 下载到系统临时目录后执行。
- 每次执行前都会清理 ZeroGit 相关临时脚本缓存。
- 插件仓库中的 [`scripts/`](./scripts) 是当前脚本基线来源；真正业务仓库如果要覆盖脚本，覆盖点在“业务仓库根目录”，不是 `scripts/` 子目录。

### 2.2 脚本钩子

以下脚本都支持在执行前后自动调用仓库根目录中的同名钩子：

- `Pre_<ScriptName>.sh`
- `Post_<ScriptName>.sh`

这套钩子机制已存在于 `gitCheck.sh`、`StartNewRelease.sh`、`StartNewHotfix.sh`、`MavenChange.sh`、`GitMergeRequest.sh`、`GenCommitMessage.sh`、`AiCodeReview.sh` 等脚本中。

### 2.3 Bash 约束

- macOS / Linux：直接使用 `bash`
- Windows：读取配置项 `zerofinanceGit.gitBash`
- `gitBash` 可以填写 Git 安装目录，也可以直接填写 `bash.exe` 路径；插件最终解析为可执行的 Git Bash
- `debug=true` 时，脚本执行会带 `bash -x`

---

## 3. 配置项

| 配置键 | 默认值 | 说明 |
|---|---|---|
| `zerofinanceGit.gitScriptsUrlPreference` | `https://gitlab.zerofinance.net/dave.zhao/deployPlugin/-/raw/main/git-flow` | 远程脚本根地址 |
| `zerofinanceGit.checkGitVersion` | `false` | 是否在执行前检查 Git 版本，要求 `>= 2.29` |
| `zerofinanceGit.debug` | `false` | 是否开启扩展调试日志，并在 Bash 中开启 `-x` |
| `zerofinanceGit.groupNames` | `a b c` | 允许选择的分组列表，空格分隔 |
| `zerofinanceGit.groupName` | 空字符串 | 默认分组；执行时仍会弹窗让用户从 `groupNames` 里选择 |
| `zerofinanceGit.gitMrAssignees` | `faker.zhou justin.wang conan.chen rain.he` | Merge Request assignee 候选列表，空格分隔 |
| `zerofinanceGit.gitBash` | 空字符串 | Windows 下 Git 目录或 `bash.exe` 路径 |

说明：

- `groupName` 已不是“唯一固定值”；当前实现会在每次需要分组的命令执行前弹出选择框。
- `groupNames` 已支持动态扩展，不再写死为 `a` / `b`。

---

## 4. 核心概念

### 4.1 分组

- 分支前缀：
  - `feature/<group>/`
  - `release/<group>/`
  - `hotfix/<group>/`
- 开发分支：
  - `develop-<group>`
- 当前实现里，除 `Finish Release` / `Finish Hotfix` / `Generate Commit Message` / `AI Code Review` 外，命令执行前都会先让用户选择分组。

### 4.2 版本规则

| 类型 | 规则 | 示例 |
|---|---|---|
| Feature | `feature/<group>/<number>-<desc>` | `feature/a/001-login` |
| Release | `release/<group>/X.Y.Z` | `release/a/1.2.0` |
| Hotfix | `hotfix/<group>/X.Y.Z` | `hotfix/a/1.2.1` |

### 4.3 版本建议逻辑

- `Start New Release`
  - 从“最新远程 tag + 所有 group 的远程 release/hotfix 分支”里找全局最大 SemVer
  - 在此基础上执行 **minor + 1，patch 归零**
  - 再避开当前 group 已存在的 release/hotfix 版本冲突
- `Start New Hotfix`
  - 必须先找到最新生产 tag
  - 在“最新生产 tag + 所有 group 的远程 release/hotfix 分支”里找全局最大 SemVer
  - 在此基础上执行 **patch + 1**
  - 再避开当前 group 已存在的 release/hotfix 版本冲突

可识别的 tag 形态：

- `vX.Y.Z`
- `release/<group>/X.Y.Z-YYYYMMDDHHmm`
- `hotfix/<group>/X.Y.Z-YYYYMMDDHHmm`

---

## 5. 命令清单

### 5.1 Git / AI / CI 辅助命令

| 命令 ID | 展示名 | 脚本/来源 | 关键行为 |
|---|---|---|---|
| `extension.GenerateCommitMessage` | Generate Commit Message | `GenCommitMessage.sh` | 仅对已暂存变更运行；依赖本机 `codex` 命令；默认模型 `gpt-5.4`；只生成 message，不自动 commit |
| `extension.AiCodeReview` | AI Code Review | `AiCodeReview.sh` | 仅对已暂存变更运行；依赖本机 `codex` 命令；要求已安装 `code-review-expert` skill；只做 review，不自动 commit |
| `extension.MavenChange` | Maven Change | `MavenChange.sh` | 选择 Maven 子项目、选择 `release/snapshot`、输入版本号；`release` 会校验 Nexus2 中是否已存在该版本，并执行 `mvn deploy` |
| `extension.GitMergeRequest` | Merge Request | `GitMergeRequest.sh` | 通过 GitLab push options 创建 MR；默认目标分支 `develop-<group>`；要求用户选择或输入 assignee |
| `extension.RunGitlabCiBaseExecCmd` | Run CI Command | 直接解析 `.gitlab-ci.yml` | 从 `BASE_EXEC_CMD` 候选中选一条，在终端执行原始命令 |
| `extension.GitFlowGuideline` | GitFlow Guideline | Feishu 链接 | 打开团队 GitFlow 指南 |

`Run CI Command` 的 `BASE_EXEC_CMD` 来源包括：

- 根级 `BASE_EXEC_CMD`
- 根级 `variables.BASE_EXEC_CMD`
- 各 job 的 `variables.BASE_EXEC_CMD`

### 5.2 ZeroGit Flow 命令

| 命令 ID | 展示名 | 脚本 | 执行方式 | 当前行为摘要 |
|---|---|---|---|---|
| `extension.StartNewFeature` | Start New Feature | `StartNewFeature.sh` | 终端 | 选择 group，输入 `feature/<group>/001-desc`，通过 `gitCheck` 后执行 |
| `extension.FinishFeature` | Finish Feature | `FinishFeature.sh` | 终端 | 先确认已 MR 到 `develop-<group>`，再从本地 feature 列表中选择分支 |
| `extension.RebaseFeature` | Rebase Feature | `RebaseFeature.sh` | 终端 | 不跑 `gitCheck`；要求当前分支必须是 `feature/<group>/...` |
| `extension.StartNewRelease` | Start New Release | `StartNewRelease.sh` | 终端 | 先确认提测时机；若发现依赖里有 `-SNAPSHOT` 会二次确认；建议版本按最新 tag/release/hotfix 自动推导 |
| `extension.FinishRelease` | Finish Release | `FinishRelease.sh` | 同步执行并解析输出 | 不先选 group；先确认 Maintainer 权限与上线完成，再选择 release 分支；执行后解析剩余 release/hotfix 分支提示 |
| `extension.StartNewHotfix` | Start New Hotfix | `StartNewHotfix.sh` | 终端 | 先确认主干是否已及时回合；若依赖里有 `-SNAPSHOT` 会二次确认；必须基于最新生产 tag 创建 |
| `extension.FinishHotfix` | Finish Hotfix | `FinishRelease.sh` | 同步执行并解析输出 | 不先选 group；流程同 Finish Release，但目标分支为 hotfix |

---

## 6. 各命令的关键细节

### 6.1 Start New Feature

- 输入值必须以 `feature/<group>/` 开头
- 后缀必须满足 `^\d+-\S.*$`
- 脚本参数：`[groupName, fullFeatureName]`

### 6.2 Finish Feature

- 只从**本地分支**中列出 `feature/<group>/` 前缀的候选
- 按数字前缀降序排列
- 脚本参数：`[groupName, selectedFeatureBranch]`

### 6.3 Rebase Feature

- 当前分支必须匹配 `feature/<group>/`
- 脚本参数：`[groupName, currentBranch]`
- 这是少数不跑 `gitCheck` 的命令

### 6.4 Merge Request

- 目标分支默认 `develop-<group>`
- assignee 必须从列表中选择或手动输入，不能为空
- 脚本会读取最新一次非 `MR-` 前缀提交信息作为 MR title
- 若当前分支没有可推送提交，会自动创建一个空提交触发 MR
- 脚本参数：`[groupName, assignee]`

### 6.5 Maven Change

- 插件会尝试从当前选择目录向上找到最近的有效 Maven 项目
- 若从仓库根触发且存在多个 Maven 子项目，会先弹窗选择子项目
- `snapshot`：自动建议 `patch + 1` 后追加 `-SNAPSHOT`
- `release`：
  - 当前版本必须是 `-SNAPSHOT` 或 `-RCN`
  - `-SNAPSHOT` 会建议变为 `-RC1`
  - `-RCN` 会建议变为 `-RC(N+1)`
  - 会先访问 Nexus2 校验版本是否已存在
- 脚本参数：`[groupName, mavenVersion]`

### 6.6 Start New Release

- 先弹出“是否已执行 FinishFeature、是否准备提测”的确认
- 若仓库中的依赖或插件版本引用包含 `-SNAPSHOT`，会提示用户确认
- `StartNewRelease.sh` 在 Maven 项目中会自动：
  - 创建 `release/<group>/X.Y.Z`
  - 将 `pom.xml` 版本改为 `X.Y.Z-RC1`
  - 自动提交 `chore: set version to X.Y.Z-RC1`
  - push 到远端并设置 upstream
- 脚本参数：`[groupName, fullReleaseName]`

### 6.7 Finish Release

- 不需要先选择 group
- 只允许在确认“有 Maintainer 权限，且此功能仅用于处理 CICD 自动 merge 冲突”后继续
- 必须再确认“运维已完成上线”
- 通过同步执行脚本获取 stdout/stderr，再解析 `REMAINING_RELEASES:` 或等价文本
- 脚本参数：`[selectedReleaseBranch]`

### 6.8 Start New Hotfix

- 必须先确认主干回合情况
- 若仓库中的依赖或插件版本引用包含 `-SNAPSHOT`，会提示用户确认
- 会先找最新生产 tag，再提示“即将基于该 tag 创建 hotfix”
- 脚本会执行 `git switch -c <hotfix> <baseTag>`
- 脚本参数：`[groupName, fullHotfixName, baseTag]`

### 6.9 Finish Hotfix

- 与 Finish Release 共用 `FinishRelease.sh`
- 不需要先选择 group
- 脚本参数：`[selectedHotfixBranch]`

---

## 7. `gitCheck` 规则

除以下命令外，执行前都需要通过 `gitCheck.sh`：

- `Generate Commit Message`
- `AI Code Review`
- `Maven Change`
- `Rebase Feature`

`gitCheck.sh` 当前检查项：

1. 工作区必须干净，不能有未提交、未暂存、未跟踪文件
2. 当前必须处于正常分支，不能是 detached HEAD
3. 当前分支必须已经配置 upstream
4. 先执行 `git fetch origin --prune`
5. 不能 behind 远端，必须先 `git pull`
6. 不能 ahead 远端，必须先 `git push`
7. 若 `checkGitVersion=true`，Git 版本必须 `>= 2.29`

---

## 8. 菜单与入口

插件当前入口分布：

- `editor/context`
- `terminal/context`
- `view/title`（Terminal 视图标题）

菜单结构：

- `ZeroGit`
- `Maven`
- `Feature`
- `Release`
- `Hotfix`
- `GitLab CI`

其中：

- `Feature` 子菜单包含 `Start New Feature`、`Finish Feature`、`Rebase Feature`、`Merge Request`
- `Release` 子菜单包含 `Start New Release`、`Finish Release`
- `Hotfix` 子菜单包含 `Start New Hotfix`、`Finish Hotfix`

---

## 9. 与旧版文档相比的关键更新

这次同步重点纠正了以下过期内容：

1. 不再是“7 个命令”，而是 13 个功能入口
2. `groupNames` / `groupName` 已支持动态配置与每次执行前选择
3. 新增 `gitMrAssignees`、`gitBash`、`Run CI Command`
4. `Start New Hotfix` 现在必须带 `baseTag`，脚本参数已是 3 个
5. `Start New Release` 已包含 Maven 项目自动改 `RC1` 并提交的逻辑
6. `Maven Change`、`Generate Commit Message`、`AI Code Review` 已成为正式能力，不再是文档缺失项
7. `Finish Release` / `Finish Hotfix` 已明确为“同步执行脚本并解析输出”的模式
