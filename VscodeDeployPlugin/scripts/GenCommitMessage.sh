#!/bin/bash

modelName="${1:-new-api/GLM-5}"

git config --global user.email "dev@zerofinance.com"
git config --global user.name "ai-dev"
git config --global pull.rebase false

if ! command -v opencode >/dev/null 2>&1; then
  echo "未检测到 opencode 命令，请先安装并确保在 PATH 中可用"
  exit 1
fi

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "当前目录不是 Git 仓库"
  exit 1
fi

if ! git diff --cached --quiet; then
  echo "检测到已暂存变更，开始生成 Commit Message..."
else
  echo "未检测到已暂存变更，请先执行 git add 后再生成 Commit Message"
  exit 1
fi

prompt=$(cat <<EOF
你是一个专业的 Git Commit 助手，必须严格遵守以下 commit message 规范：

格式：
<type>: <subject>

变更内容：
1. README.md - 删除空行
2. deploy.sh - 合并代码行，优化格式

type（必须）只允许以下值：
- feat：新功能
- fix：修复bug
- docs：文档变更
- style：代码风格调整（不影响运行）
- refactor：代码重构
- perf：性能优化
- test：增加或修改测试
- chore：构建/工具/辅助变动
- revert：回滚
- merge：合并代码
- sync：同步主线或分支的 bug

subject（必须）：
- 尽可能详细描述。总体不超过 200 个字符
- 用中文（更清晰）
- 结尾不要加句号或其他标点

变更内容（必须）：
- 以实际的为准，尽量简短描述。不超过 20 个字符
- 用中文（更清晰）
- 结尾不要加句号或其他标点

当前任务：
1. 执行 git diff --cached 查看已暂存的变更（如果没有暂存，提醒用户先 git add）
2. 分析变更内容
3. 判断最合适的 type 和 scope
4. 生成符合规范的 commit message（用中文）
5. 执行 git commit -m "commit message" 命令
EOF
)

opencode run -m "$modelName" "$prompt"
