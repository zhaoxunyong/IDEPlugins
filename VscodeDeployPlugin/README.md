# zerofinance-git

The zerofinance-git, Visual Studio Code. Learn more at https://github.com/zhaoxunyong/vs-code-git-plugin.

## publish 

```bash
npm i vsce -g
npm install
# vsce create-publisher zerofinance
#vsce delete-publisher zerofinance
#Generated token from "https://dev.azure.com/it0815/_usersSettings/tokens"
#Just login once
vsce login zerofinance
#vsce package
vsce publish
```

## Settings

Setting "terminal.integrated.shell.windows" at "settings.json":

```
#Changing "D:\\Developer\\Git\\bin\\bash.exe" as your Customizes
"terminal.integrated.shell.windows": "YourGitPath\\bin\\bash.exe"
```

Optional debug setting:

```
"zerofinanceGit.debug": true
```

## 项目功能点与技术架构分析

### 功能点

- Git Flow 六个命令入口：
  - `StartNewFeature`
  - `FinishFeature`
  - `StartNewRelease`
  - `FinishRelease`
  - `StartNewHotfix`
  - `FinishHotfix`
- 每个命令统一流程：选择工作区 -> 执行 `gitCheck` -> 调用对应 shell 脚本。
- 脚本回退机制：优先使用项目根目录脚本；不存在时从远程下载到临时目录执行。

### 技术架构

- 运行形态：VS Code Extension Host 插件（Node.js + CommonJS）。
- 入口层：`src/extension.js`，负责命令注册、状态提示、终端命令执行与流程编排。
- 辅助层：`src/myPlugin.js`，当前用于工作区选择与脚本下载。
- 执行层：外部 Shell 脚本（`StartNewFeature.sh`、`FinishFeature.sh`、`StartNewRelease.sh`、`FinishRelease.sh`、`StartNewHotfix.sh`、`FinishHotfix.sh`、`gitCheck.sh`）。

### 核心依赖

- `vscode`：命令、UI、Terminal API。
- `axios`：下载远程脚本。
- `tmp` + `fs`：临时路径管理与脚本文件读写。
- `child_process.exec`：执行检查脚本。

### 关键流程

1. 用户触发任一 Git Flow 命令（六选一）。
2. 插件选择目标工作区并执行 `gitCheck.sh`（可选 git 版本门槛检查）。
3. 插件执行对应命令脚本，具体 Git Flow 逻辑由脚本内部实现。

### 当前风险与待改进点

- 远程脚本下载并执行，存在供应链风险，建议固定可信地址并完善签名/校验机制。
- VS Code engine 与依赖版本偏旧，兼容性和维护成本需评估。
- 自动化测试覆盖较弱，当前测试主要为模板示例，建议补充关键流程测试。