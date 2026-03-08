## FinishRelease.sh 使用说明与流程梳理

本文档说明 `FinishRelease.sh` 的入参含义及整体执行流程。**Release 与 Hotfix 共用此脚本**，通过分支名前缀（`release/` 或 `hotfix/`）自动区分模式；插件与命令行均统一只调用本脚本。

---

### 1. 脚本入参

- **命令格式**

  ```bash
  ./FinishRelease.sh <branch>
  ```

- **参数说明**
- **branch**：要结束的分支名。以 `release/` 开头视为 release，以 `hotfix/` 开头视为 hotfix，须分别符合 `release/<groupName>/<version>`、`hotfix/<groupName>/<version>`，例如 `release/core/1.2.3`、`hotfix/core/1.0.1`。脚本从分支名解析 groupName 和版本号；develop 目标列表由脚本自动从远程所有以 `develop-` 开头的分支获取，无需传参。

- **约束校验**
- 若缺少分支参数，脚本会直接退出并输出 Usage。
- 分支名必须以 `release/` 或 `hotfix/` 开头，否则退出。
- 从分支名提取的版本号必须满足 SemVer 格式 `X.Y.Z`，否则退出。

---

### 2. 关键内部约定

- **develop 分支**
  - 脚本自动从远程获取所有以 `develop-` 开头的分支作为合并 master 的目标，无需传参。

- **release 分支**
  - 从入参 `releaseBranch` 解析：`release/<groupName>/<version>`，例如 `release/core/1.2.3`。

- **统一 git 操作封装**
  - 所有 `git` 命令都通过 `run_git "<描述>" <命令...>` 封装。
  - 若命令失败，输出错误信息，提示“请解决冲突/问题后重新执行 Finish Release/Hotfix”，并退出。

- **checkout_or_track_branch 函数**
  - 若本地已存在同名分支：直接 `checkout`，并将全局变量 `NEED_PULL` 置为 `1`，表示需要随后执行 `git pull`。
  - 若本地不存在，但远程 `origin/<branch>` 存在：创建本地跟踪分支 `git checkout --track -b <branch> origin/<branch>`，并将 `NEED_PULL` 置为 `0`（刚从远程创建，一般不再拉取）。
  - 若本地和远程都不存在：输出错误并退出。

---

### 3. 整体流程概览

脚本整体逻辑可以分为 7 个步骤：

1. **准备阶段：拉取远程分支信息**
2. **将目标 release 分支合并到 master，并推送**
3. **将最新 master 合并回所有 develop 分支**（远程 `develop-*`）
4. **将最新 master 合并回所有未完成的 release 分支**
5. **将最新 master 合并回所有未完成的 hotfix 分支**
6. **在 master 上打 tag 并推送**
7. **切换到 master 分支，删除已完成的 release 分支（本地和远程）**

下面对每个步骤做详细说明。

---

### 4. 详细步骤说明

#### 步骤 1：准备阶段

- 输出当前参数：`target branch: <branch> (detected mode: <release|hotfix>)`；develop 列表在步骤 3 从远程 `develop-*` 获取。
- 执行：
  - `git fetch origin --prune`
  - 从远程拉取最新分支信息，并清理已经在远程删除的本地远程跟踪分支引用。

#### 步骤 2：release → master 合并、打 Tag、推送

1. **检出目标 release 分支**
   - `checkout_or_track_branch "$releaseBranch"`
   - 若本地已存在，则随后会 `git pull origin <releaseBranch>`。

2. **检出 master 并拉取最新**
   - `checkout_or_track_branch "master"`
   - 若本地 master 已存在，则随后会 `git pull origin master`。

3. **将 release 分支合并到 master**
   - `git merge --no-ff "$releaseBranch"`：保留合并记录，方便回溯。

4. **处理 Tag**
   - 同步远程 Tag：`git fetch origin --tags --prune-tags`
   - 构造 Tag 名：`v<releaseVersion>`，例如 `v1.2.3`。
   - 若远程已存在同名 Tag：直接退出，避免重复打 Tag。
   - 若本地存在但远程没有（视为陈旧 Tag）：先删除本地 Tag 再重建。
   - 创建新的发布 Tag：`git tag -a "v<releaseVersion>" -m "Release <releaseVersion>"`。

5. **推送 master 与 tags**
   - `git push origin master --tags`
   - 将最新的 master 以及新创建的 Tag 推送到远程。

#### 步骤 3：删除已完成的 release 分支

- 在本地删除 release 分支：
  - `git branch -d "$releaseBranch"`
- 在远程删除 release 分支：
  - `git push origin --delete "$releaseBranch"`

#### 步骤 4：同步 master → 各 develop 分支

1. **刷新远程分支信息**
   - 再次执行 `git fetch origin --prune`，确保删除完 release 分支后远程信息最新。

2. **计算目标 develop 分支列表**
   - 将 `groupsList` 以逗号分隔为多个 group，去掉空格。
   - 对每个 group 生成 `develop-<group>` 分支名。
   - 仅在远程存在相应 `origin/develop-<group>` 时，才加入待处理列表。

3. **依次处理每个 develop 分支**
   - `checkout_or_track_branch "$branch"`
   - 若本地已存在，对应分支执行 `git pull origin "$branch"`。
   - 合并 master：`git merge --no-ff master`
   - 推送到远程：`git push origin "$branch"`

#### 步骤 5：同步 master → 所有未完成的 release 分支

1. **获取所有远程 release 分支列表**
   - 使用 `git for-each-ref` 从 `refs/remotes/origin/release/` 列出所有 release 分支。
   - 去掉前缀 `origin/`，保留形如 `release/<group>/<version>` 的短分支名。

2. **依次处理每个 release 分支**
   - `checkout_or_track_branch "$branch"`
   - 如有需要，对应分支执行 `git pull origin "$branch"`。
   - 合并 master：`git merge --no-ff master`
   - 推送到远程：`git push origin "$branch"`
   - 将该分支名加入 `remainingVersions` 数组，记录为“仍然存在的发布分支”。

#### 步骤 6：同步 master → 所有未完成的 hotfix 分支

1. **获取所有远程 hotfix 分支列表**
   - 同样通过 `git for-each-ref` 从 `refs/remotes/origin/hotfix/` 读取，得到形如 `hotfix/<...>` 的短分支名。

2. **依次处理每个 hotfix 分支**
   - `checkout_or_track_branch "$branch"`
   - 如有需要，对应分支执行 `git pull origin "$branch"`。
   - 合并 master：`git merge --no-ff master`
   - 推送到远程：`git push origin "$branch"`
   - 同样记录进 `remainingVersions`，后续统一输出。

#### 步骤 7：切回当前组 develop 分支并输出剩余分支

1. **切回当前组 develop 分支**
   - `checkout_or_track_branch "$developBranch"`，即 `develop-<groupName>`。

2. **输出剩余的 release / hotfix 分支**
   - 最后输出一行：
     - `REMAINING_RELEASES:...`
   - 内容为 `remainingVersions` 中记录的所有仍然存在的 release / hotfix 分支名，使用 `/` 作为分隔符。

---

### 5. 使用建议与注意事项

- **提前确保本地工作区干净**
  - 执行脚本前请确认本地没有未提交或未暂存的修改，避免在切换分支时被打断。

- **冲突处理**
  - 如果在合并过程中产生冲突，`run_git` 会终止脚本并提示。
  - 需手动解决冲突、提交后，再重新执行 `FinishRelease.sh`。

- **Tag 策略**
  - 同一个版本号的 Tag 不允许在远程重复存在。
  - 如本地已存在但远程没有，脚本会自动删除本地旧 Tag 后重新创建，以保证 Tag 语义清晰。

- **多组 develop 同步**
  - 若希望一个 release 完成后同步到多个业务组的 `develop` 分支，可以在第三个参数中传入多个分组名，例如：
    - `./FinishRelease.sh core release/core/1.2.3 "core,app,ops"`

