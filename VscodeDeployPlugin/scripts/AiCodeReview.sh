#!/bin/bash
# 本地对已暂存变更做 AI code review（不发送飞书、不操作 git commit）。
# 用法：./AiCodeReview.sh [模型名]   默认模型与 GenCommitMessage.sh 一致：gpt-5.4

#export PATH="/usr/local/bin:/usr/bin:~/.codex/bin:$PATH"
export PATH="/usr/local/bin:/usr/bin:~/AppData/Roaming/npm:~/.nvm/versions/node/v22.22.0/bin:$PATH"

modelName="${1:-gpt-5.4}"

if ! command -v codex >/dev/null 2>&1; then
  echo "未检测到 codex 命令，请先安装并确保在 PATH 中可用"
  exit 1
fi

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "当前目录不是 Git 仓库"
  exit 1
fi

# code-review-expert 检测路径（任一处存在 SKILL.md 即通过）：
#   全局：$CODEX_HOME/skills（Codex）、$HOME/.agents/skills（npx skills / Agent 常见全局目录）
#   项目：Git 仓库根下 .agents/skills、.codex/skills（把 skill 放进仓库时）
# 说明见 https://github.com/sanyuan0704/sanyuan-skills
# Windows 用户使用 Git Bash 时：未设置 CODEX_HOME 时与 Linux/macOS 一样用 $HOME/.codex（多为 /c/Users/.../.codex）；
# 若你在「系统环境变量」里把 CODEX_HOME 设成 C:\... 形式，需转成 Unix 路径才能 [ -f ] 命中，下面用 cygpath（Git Bash 自带）处理。
codex_home="${CODEX_HOME:-$HOME/.codex}"
if command -v cygpath >/dev/null 2>&1 && [ -n "${CODEX_HOME:-}" ]; then
  case "$CODEX_HOME" in
    [a-zA-Z]:[/\\]*|*\\*)
      _codex_u=$(cygpath -u "$CODEX_HOME" 2>/dev/null)
      [ -n "$_codex_u" ] && codex_home="$_codex_u"
      ;;
  esac
fi
_git_top=$(git rev-parse --show-toplevel 2>/dev/null)
_skill_dirs=(
  "$codex_home/skills/code-review-expert"
  "$HOME/.agents/skills/code-review-expert"
)
if [ -n "$_git_top" ]; then
  _skill_dirs+=(
    "$_git_top/.agents/skills/code-review-expert"
    "$_git_top/.codex/skills/code-review-expert"
  )
fi
skill_found=
for _skill_dir in "${_skill_dirs[@]}"; do
  if [ -f "$_skill_dir/SKILL.md" ]; then
    skill_found=1
    break
  fi
done
if [ -z "$skill_found" ]; then
  echo "未检测到 code-review-expert skill，请先安装后再运行本脚本。" >&2
  echo "" >&2
  echo "仓库与能力说明：https://github.com/sanyuan0704/sanyuan-skills" >&2
  echo "" >&2
  echo "安装方式：" >&2
  echo "  文档中的 npx 方式（多适用于 Agent 终端，常安装到 .agents/skills）：" >&2
  echo "     npx skills add sanyuan0704/sanyuan-skills --path skills/code-review-expert" >&2
  echo "  选择 code-review-expert 技能即可。" >&2
  exit 1
fi
unset skill_found _skill_dir _git_top codex_home _codex_u _skill_dirs

if ! git diff --cached --quiet; then
  echo "检测到已暂存变更，开始 AI Code Review..."
else
  echo "未检测到已暂存变更，请先执行 git add 后再运行本脚本"
  exit 1
fi

# diff 写入临时文件，再通过 stdin 传给 codex，避免超长 argv（ARG_MAX）与 bash 巨型变量
DIFF_FILE=$(mktemp)
trap 'rm -f "$DIFF_FILE"' EXIT

git --no-pager diff --cached > "$DIFF_FILE"

{
  cat <<'EOF'
使用code-review-expert skills，对下面「Code diff」中的已暂存变更进行 code review，用中文回复：
- 仅根据「Code diff」中的内容进行 review，不要执行 git 命令、不要使用 git history 或工作区其它未暂存变更；只分析本次暂存区 diff。
- 如果发现 P0/P1 级别的严重问题 → 输出 FAIL 并说明原因。否则输出 PASS
- 先写结论（PASS 或 FAIL），再详细说明
- 始终不要自动 git commit

Code diff:
EOF
  cat "$DIFF_FILE"
  printf '\nAnswer in Chinese.\n'
} | codex exec -m "$modelName" -