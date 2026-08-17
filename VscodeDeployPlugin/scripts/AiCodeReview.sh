#!/bin/bash

# 当前工作目录（$PWD）下若存在 Pre_<本脚本文件名> 则执行，否则跳过
_VSDEP_PRE="$PWD/Pre_$(basename "${BASH_SOURCE[0]:-$0}")"
if [ -f "$_VSDEP_PRE" ]; then
  if [ -x "$_VSDEP_PRE" ]; then
    "$_VSDEP_PRE" "$@" || exit $?
  else
    bash "$_VSDEP_PRE" "$@" || exit $?
  fi
fi
unset _VSDEP_PRE

# 本地对已暂存变更或指定提交历史做 repo-aware AI code review（不发送飞书、不操作 git commit）。
# 用法：./AiCodeReview.sh [提交范围]   模型固定为 gpt-5.6-terra

#export PATH="/usr/local/bin:/usr/bin:~/.codex/bin:$PATH"
export PATH="/usr/local/bin:/usr/bin:~/AppData/Roaming/npm:~/.nvm/versions/node/v22.22.0/bin:$PATH"

modelName="gpt-5.6-terra"
commitRange="${1:-}"
revision_args=()
if [ -n "$commitRange" ]; then
  read -r -a revision_args <<< "$commitRange"
fi

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

if [ "${#revision_args[@]}" -eq 0 ]; then
  if git diff --cached --quiet; then
    echo "未检测到已暂存变更，请先执行 git add 后再运行本脚本"
    exit 1
  fi
  echo "检测到已暂存变更，开始 repo-aware AI Code Review..."
else
  echo "检测到提交范围 ${commitRange}，开始 repo-aware AI Code Review..."
fi

GIT_TOP=$(git rev-parse --show-toplevel)
PROJECT_NAME=$(basename "$GIT_TOP")
TMP_WORK_DIR="$HOME/.codex/tmp/${PROJECT_NAME}/local-ai-code-review/$(date +%Y%m%d)/$$"
mkdir -p "$TMP_WORK_DIR"
echo "[local-ai-review 调试] 临时工作目录: $TMP_WORK_DIR" >&2

AI_CODE_REVIEW_TIMEOUT_SECONDS="${AI_CODE_REVIEW_TIMEOUT_SECONDS:-${MR_AI_REVIEW_TIMEOUT_SECONDS:-300}}"
echo "[local-ai-review 调试] 使用超时时间: ${AI_CODE_REVIEW_TIMEOUT_SECONDS}s" >&2
echo "[local-ai-review 调试] 使用模型: ${modelName}" >&2
[ "${#revision_args[@]}" -eq 0 ] || echo "[local-ai-review 调试] 提交范围: ${commitRange}" >&2

download_ai_review_risk_learnings() {
  local tmp_work_dir="$1"
  local local_risk_file="$GIT_TOP/ai-review/ai-risk-learnings.md"
  local risk_url="${AI_REVIEW_RISK_LEARNINGS_URL:-https://gitlab.zerofinance.net/dave.zhao/deployPlugin/-/raw/main/ai-review/ai-risk-learnings.md}"
  local risk_file="$tmp_work_dir/ai-risk-learnings.md"

  if [ -s "$local_risk_file" ]; then
    printf '%s\n' "$local_risk_file"
    return 0
  fi

  if [ -n "${GITLAB_TOKEN:-}" ]; then
    curl -fsSL --retry 2 --retry-delay 1 -H "PRIVATE-TOKEN: ${GITLAB_TOKEN}" -o "$risk_file" "$risk_url" || return 1
  else
    curl -fsSL --retry 2 --retry-delay 1 -o "$risk_file" "$risk_url" || return 1
  fi

  [ -s "$risk_file" ] || return 1
  printf '%s\n' "$risk_file"
}

append_ai_review_risk_learnings_section() {
  local prompt_file="$1"
  local risk_file="$2"

  {
    echo
    echo "以下是本次 AI review 必须参考的风险库："
    cat "$risk_file"
  } >> "$prompt_file"
}

resolve_commit_ai_review_range() {
  local requested_base_sha

  case "${#revision_args[@]}" in
    1)
      COMMIT_REVIEW_HEAD_SHA=$(git rev-parse --verify "${revision_args[0]}^{commit}") || return
      COMMIT_REVIEW_BASE_SHA=$(git rev-parse --verify "${COMMIT_REVIEW_HEAD_SHA}^") || return
      ;;
    2)
      requested_base_sha=$(git rev-parse --verify "${revision_args[0]}^{commit}") || return
      COMMIT_REVIEW_HEAD_SHA=$(git rev-parse --verify "${revision_args[1]}^{commit}") || return
      COMMIT_REVIEW_BASE_SHA=$(git merge-base "$requested_base_sha" "$COMMIT_REVIEW_HEAD_SHA") || return
      ;;
    *)
      echo "提交范围仅支持单个 commit 或 BASE HEAD 两个 commit" >&2
      return 1
      ;;
  esac

  COMMIT_REVIEW_DIFF_RANGE="${COMMIT_REVIEW_BASE_SHA}...${COMMIT_REVIEW_HEAD_SHA}"
}

build_local_ai_review_context() {
  local tmp_work_dir="$1"
  local context_file="$2"
  local changed_files_file="$tmp_work_dir/changed-files.txt"
  local changed_lines_file="$tmp_work_dir/changed-lines.patch"
  local diff_stat_file="$tmp_work_dir/diff-stat.txt"
  local name_status_file="$tmp_work_dir/name-status.txt"
  if [ "${#revision_args[@]}" -gt 0 ]; then
    resolve_commit_ai_review_range || return
    git diff --name-only "$COMMIT_REVIEW_DIFF_RANGE" > "$changed_files_file" || return
    git diff --unified=0 "$COMMIT_REVIEW_DIFF_RANGE" > "$changed_lines_file" || return
    git diff --stat "$COMMIT_REVIEW_DIFF_RANGE" > "$diff_stat_file" || return
    git diff --name-status "$COMMIT_REVIEW_DIFF_RANGE" > "$name_status_file" || return

    {
      echo "Review source: Git commit diff"
      echo "Repository: ${GIT_TOP}"
      echo "BASE_SHA: ${COMMIT_REVIEW_BASE_SHA}"
      echo "HEAD_SHA: ${COMMIT_REVIEW_HEAD_SHA}"
      echo "Diff range: ${COMMIT_REVIEW_DIFF_RANGE}"
      echo
      echo "Changed files file: ${changed_files_file}"
      echo "Changed lines patch file: ${changed_lines_file}"
      echo
      echo "Diff stat:"
      cat "$diff_stat_file"
      echo
      echo "Name status:"
      cat "$name_status_file"
    } > "$context_file"
    return
  fi

  local head_sha=""
  git diff --cached --name-only > "$changed_files_file"
  git diff --cached --unified=0 > "$changed_lines_file"
  git diff --cached --stat > "$diff_stat_file"
  git diff --cached --name-status > "$name_status_file"
  head_sha=$(git rev-parse --verify HEAD 2>/dev/null || printf 'UNBORN_HEAD')

  {
    echo "Review source: local staged changes"
    echo "Repository: ${GIT_TOP}"
    echo "BASE: HEAD"
    echo "HEAD_SHA: ${head_sha}"
    echo "Index: staged changes"
    echo
    echo "Changed files file: ${changed_files_file}"
    echo "Changed lines patch file: ${changed_lines_file}"
    echo
    echo "Diff stat:"
    cat "$diff_stat_file"
    echo
    echo "Name status:"
    cat "$name_status_file"
  } > "$context_file"
}

build_commit_ai_review_prompt() {
  local context_file="$1"
  local prompt_file="$2"
  local risk_learnings_file="${3:-}"

  {
    printf '%s\n' \
      '使用 code-review-expert skills 对当前 Git 提交记录做 code review，用中文回复。' \
      '' \
      '你运行在本地仓库目录里。请做 repo-aware review：' \
      '- 评审对象只限 BASE_SHA...HEAD_SHA 这一次 commit diff。' \
      '- 可以读取仓库其它文件作为上下文，但 findings 必须只针对本次 commit diff 直接引入或暴露的问题。' \
      '- 不要把历史存量问题、非本次 commit diff 修改行问题、暂存变更、未暂存变更或纯风格问题作为 finding 输出，也不要影响 VERDICT。' \
      '- 如果上下文里发现旧代码风险，但本次 commit diff 没有改到相关逻辑，默认不要输出。' \
      '- 允许执行只读分析命令：git diff、git show、git status、rg、sed、cat、find、wc。' \
      '- 不要修改文件，不要安装依赖，不要联网，不要执行构建/测试，不要 push/commit。' \
      '- 请优先查看 git diff --stat BASE_SHA...HEAD_SHA、git diff --name-status BASE_SHA...HEAD_SHA，再按风险选择文件深入审查。' \
      '- 如果发现 P0 级别的严重问题 -> 结论为 FAIL，并在理由中说明原因。' \
      '- 如果没有发现 P0 级别的严重问题 -> 结论为 PASS。' \
      '- 如果发现 P1 级别的问题 -> 仍然判定为 PASS，但在理由中重点提醒用户关注这些问题是否需要修复，并建议用户在 MR Commit 中标注 P1 级别的问题是否需要解决。' \
      '- 输出格式必须满足：必须包含且仅包含一行结论，形如「VERDICT: PASS」或「VERDICT: FAIL」（全大写，该行前后不要拼接其它文字；强烈建议放在输出的第一行）；其余行再输出详细说明，可以包含任意内容（包括 PASS/FAIL 等词）。' \
      '' \
      '以下是本次 review 的上下文：'
  } > "$prompt_file"
  cat "$context_file" >> "$prompt_file"
  if [ -n "$risk_learnings_file" ]; then
    append_ai_review_risk_learnings_section "$prompt_file" "$risk_learnings_file"
  fi
}

build_staged_ai_review_prompt() {
  local context_file="$1"
  local prompt_file="$2"
  local risk_learnings_file="${3:-}"

  {
    printf '%s\n' \
      '使用 code-review-expert skills 对当前本地 Git 仓库的已暂存变更做 code review，用中文回复。' \
      '' \
      '你运行在本地仓库目录里。请做 repo-aware review：' \
      '- 评审对象只限当前暂存区（git diff --cached）这一次变更。' \
      '- 可以读取仓库其它文件作为上下文，但 findings 必须只针对本次已暂存 diff 新增/修改/删除直接引入或暴露的问题。' \
      '- 不要把历史存量问题、未暂存变更、非本次暂存修改行问题、纯风格问题作为 finding 输出，也不要影响 VERDICT。' \
      '- 本地工作区可能存在未暂存内容；查看本次变更文件的新版本时，优先用 git show :<path> 读取暂存区内容，避免把未暂存内容当作评审对象。' \
      '- 请优先查看 git diff --cached --stat、git diff --cached --name-status，再按风险选择文件深入审查。' \
      '- 如果上下文里发现旧代码风险，但本次评审范围没有改到相关逻辑，默认不要输出。' \
      '- 允许执行只读分析命令：git diff、git log、git show、git status、rg、sed、cat、find、wc。' \
      '- 不要修改文件，不要安装依赖，不要联网，不要 push/commit。' \
      '- 如果发现 P0 级别的严重问题 -> 结论为 FAIL，并在理由中说明原因。' \
      '- 如果没有发现 P0 级别的严重问题 -> 结论为 PASS。' \
      '- 如果发现 P1 级别的问题 -> 仍然判定为 PASS，但在理由中重点提醒用户关注这些问题是否需要修复，并建议用户在 MR Commit 中标注 P1 级别的问题是否需要解决。' \
      '- 输出格式必须满足：必须包含且仅包含一行结论，形如「VERDICT: PASS」或「VERDICT: FAIL」（全大写，该行前后不要拼接其它文字；强烈建议放在输出的第一行）；其余行再输出详细说明，可以包含任意内容（包括 PASS/FAIL 等词）。' \
      '' \
      '以下是本次 review 的上下文：'
  } > "$prompt_file"
  cat "$context_file" >> "$prompt_file"
  if [ -n "$risk_learnings_file" ]; then
    append_ai_review_risk_learnings_section "$prompt_file" "$risk_learnings_file"
  fi
}

build_ai_tool_execution_exception_message() {
  local tool_name="$1"
  local exit_code="$2"
  local debug_file="${3:-}"
  local output_file="${4:-}"

  {
    echo "${tool_name} 执行异常(exit_code=${exit_code})，请人工检查。"
    if [ -n "$output_file" ] && [ -s "$output_file" ]; then
      echo
      echo "--- last message ---"
      cat "$output_file"
    fi
    if [ -n "$debug_file" ] && [ -s "$debug_file" ]; then
      echo
      echo "--- debug log tail ---"
      tail -n 120 "$debug_file"
    fi
  }
}

publish_local_ai_review_artifacts() {
  local output_file="$1"
  local debug_file="$2"

  echo "[local-ai-review] review 内容临时文件: $output_file" >&2
  echo "[local-ai-review 调试] debug 临时文件: $debug_file" >&2
}

REVIEW_CONTEXT_FILE=$(mktemp -p "$TMP_WORK_DIR" review-context.XXXXXX.txt)
REVIEW_OUTPUT_FILE=$(mktemp -p "$TMP_WORK_DIR" review-content.XXXXXX.md)
REVIEW_DEBUG_FILE=$(mktemp -p "$TMP_WORK_DIR" review-debug.XXXXXX.log)
REVIEW_PROMPT_FILE=$(mktemp -p "$TMP_WORK_DIR" review-prompt.XXXXXX.txt)

build_local_ai_review_context "$TMP_WORK_DIR" "$REVIEW_CONTEXT_FILE" || exit $?
risk_learnings_file=$(download_ai_review_risk_learnings "$TMP_WORK_DIR") || {
  echo "[local-ai-review] 无法读取或下载 AI review 风险库: ${AI_REVIEW_RISK_LEARNINGS_URL:-https://gitlab.zerofinance.net/dave.zhao/deployPlugin/-/raw/main/ai-review/ai-risk-learnings.md}" >&2
  exit 1
}
echo "[local-ai-review 调试] AI review 风险库: ${risk_learnings_file}" >&2
if [ "${#revision_args[@]}" -gt 0 ]; then
  build_commit_ai_review_prompt "$REVIEW_CONTEXT_FILE" "$REVIEW_PROMPT_FILE" "$risk_learnings_file"
else
  build_staged_ai_review_prompt "$REVIEW_CONTEXT_FILE" "$REVIEW_PROMPT_FILE" "$risk_learnings_file"
fi
echo "[local-ai-review 调试] REVIEW_CONTEXT_FILE: ${REVIEW_CONTEXT_FILE}" >&2

WORKTREE_STATUS_BEFORE=$(git status --porcelain=v1)
case $- in
  *e*) _VSDEP_HAD_ERREXIT=1 ;;
  *) _VSDEP_HAD_ERREXIT=0 ;;
esac
set +e
if command -v timeout >/dev/null 2>&1; then
  timeout "${AI_CODE_REVIEW_TIMEOUT_SECONDS}" codex exec \
    -C "$GIT_TOP" \
    -m "$modelName" \
    --dangerously-bypass-approvals-and-sandbox \
    --output-last-message "$REVIEW_OUTPUT_FILE" \
    - <"$REVIEW_PROMPT_FILE" >"$REVIEW_DEBUG_FILE" 2>&1
  CODEX_EXIT_CODE=$?
else
  codex exec \
    -C "$GIT_TOP" \
    -m "$modelName" \
    --dangerously-bypass-approvals-and-sandbox \
    --output-last-message "$REVIEW_OUTPUT_FILE" \
    - <"$REVIEW_PROMPT_FILE" >"$REVIEW_DEBUG_FILE" 2>&1
  CODEX_EXIT_CODE=$?
fi
if [ "$_VSDEP_HAD_ERREXIT" -eq 1 ]; then
  set -e
else
  set +e
fi
unset _VSDEP_HAD_ERREXIT
WORKTREE_STATUS_AFTER=$(git status --porcelain=v1)

REVIEW_OUTPUT=$(cat "$REVIEW_OUTPUT_FILE" 2>/dev/null || true)
VERDICT_LINE=$(printf '%s\n' "$REVIEW_OUTPUT" | grep -m1 -iE '^VERDICT:[[:space:]]*(PASS|FAIL)[[:space:]]*$' || true)
VERDICT="UNKNOWN"
if printf '%s\n' "$VERDICT_LINE" | grep -qiE '^VERDICT:[[:space:]]*PASS[[:space:]]*$'; then
  VERDICT="PASS"
elif printf '%s\n' "$VERDICT_LINE" | grep -qiE '^VERDICT:[[:space:]]*FAIL[[:space:]]*$'; then
  VERDICT="FAIL"
fi

_VSDEP_MAIN_EXIT=0
if [ "$CODEX_EXIT_CODE" -eq 124 ]; then
  echo "[local-ai-review] codex exec 超时(${AI_CODE_REVIEW_TIMEOUT_SECONDS}s)，已强制终止，请人工检查。" >&2
  _VSDEP_MAIN_EXIT=1
elif [ "$CODEX_EXIT_CODE" -ne 0 ]; then
  CODEX_EXCEPTION_MESSAGE=$(build_ai_tool_execution_exception_message "codex" "$CODEX_EXIT_CODE" "$REVIEW_DEBUG_FILE" "$REVIEW_OUTPUT_FILE")
  printf '%s\n' "$CODEX_EXCEPTION_MESSAGE" >&2
  _VSDEP_MAIN_EXIT=1
elif [ "$WORKTREE_STATUS_AFTER" != "$WORKTREE_STATUS_BEFORE" ]; then
  {
    echo "Codex review 不应修改工作区，但检测到 Git 状态变化。"
    echo
    echo "--- before ---"
    printf '%s\n' "$WORKTREE_STATUS_BEFORE"
    echo
    echo "--- after ---"
    printf '%s\n' "$WORKTREE_STATUS_AFTER"
  } > "$REVIEW_DEBUG_FILE"
  CODEX_EXCEPTION_MESSAGE=$(build_ai_tool_execution_exception_message "codex" "workspace-modified" "$REVIEW_DEBUG_FILE")
  printf '%s\n' "$CODEX_EXCEPTION_MESSAGE" >&2
  _VSDEP_MAIN_EXIT=1
elif [ "$VERDICT" = "UNKNOWN" ]; then
  echo "[local-ai-review] AI review 输出缺少有效结论行（需要形如：VERDICT: PASS 或 VERDICT: FAIL，且独占一行）" >&2
  _VSDEP_MAIN_EXIT=1
else
  printf '%s\n' "$REVIEW_OUTPUT"
  if [ "$VERDICT" = "FAIL" ]; then
    echo "AI review failed - serious issues found" >&2
    _VSDEP_MAIN_EXIT=1
  fi
fi

publish_local_ai_review_artifacts "$REVIEW_OUTPUT_FILE" "$REVIEW_DEBUG_FILE"

# 当前工作目录（$PWD）下若存在 Post_<本脚本文件名> 则执行，否则跳过
_VSDEP_POST="$PWD/Post_$(basename "${BASH_SOURCE[0]:-$0}")"
if [ -f "$_VSDEP_POST" ]; then
  if [ -x "$_VSDEP_POST" ]; then
    "$_VSDEP_POST" "$@"
  else
    bash "$_VSDEP_POST" "$@"
  fi
fi
unset _VSDEP_POST

exit "$_VSDEP_MAIN_EXIT"
