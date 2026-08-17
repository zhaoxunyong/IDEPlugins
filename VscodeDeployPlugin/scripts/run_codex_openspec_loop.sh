#!/bin/bash

set -euo pipefail

CHANGE_NAME=""
PROMPT_FILE=""
REPO_ROOT=""
MAX_ITERATIONS="${RALPH_MAX_ITERATIONS:-200}"
SLEEP_SECONDS="${RALPH_SLEEP_SECONDS:-2}"
CODEX_MODEL="${RALPH_CODEX_MODEL:-gpt-5.6-terra}"
LOG_DIR=""
LOG_FILE=""
TIMESTAMP=""
SAFE_CHANGE_NAME=""
LOOP_START_TS=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --change-name)
      CHANGE_NAME="$2"
      shift 2
      ;;
    --prompt-file)
      PROMPT_FILE="$2"
      shift 2
      ;;
    --repo-root)
      REPO_ROOT="$2"
      shift 2
      ;;
    *)
      printf '未知参数：%s\n' "$1" >&2
      exit 1
      ;;
  esac
done

if [[ -z "$CHANGE_NAME" || -z "$PROMPT_FILE" || -z "$REPO_ROOT" ]]; then
  printf '%s\n' '用法：run_codex_openspec_loop.sh --change-name <name> --prompt-file <path> --repo-root <path>' >&2
  exit 1
fi

SAFE_CHANGE_NAME="${CHANGE_NAME//[![:alnum:]._-]/_}"
if [[ -z "$SAFE_CHANGE_NAME" ]]; then
  SAFE_CHANGE_NAME="change"
fi

TIMESTAMP="$(date '+%Y%m%d-%H%M%S')"
LOG_DIR="$REPO_ROOT/.ralph/logs"
LOG_FILE="$LOG_DIR/${SAFE_CHANGE_NAME}-${TIMESTAMP}.log"

mkdir -p "$LOG_DIR"

current_epoch_seconds() {
  date '+%s'
}

format_elapsed_seconds() {
  local total_seconds="$1"
  local hours=0
  local minutes=0
  local seconds=0

  if (( total_seconds < 0 )); then
    total_seconds=0
  fi

  hours=$((total_seconds / 3600))
  minutes=$(((total_seconds % 3600) / 60))
  seconds=$((total_seconds % 60))

  if (( hours > 0 )); then
    printf '%sh%sm%ss' "$hours" "$minutes" "$seconds"
    return
  fi

  if (( minutes > 0 )); then
    printf '%sm%ss' "$minutes" "$seconds"
    return
  fi

  printf '%ss' "$seconds"
}

log_printf() {
  printf "$@" | tee -a "$LOG_FILE"
}

log_error_printf() {
  printf "$@" | tee -a "$LOG_FILE" >&2
}

log_total_elapsed() {
  local end_ts=0
  local total_seconds=0

  end_ts="$(current_epoch_seconds)"
  total_seconds=$((end_ts - LOOP_START_TS))
  log_printf 'Total elapsed: %s\n' "$(format_elapsed_seconds "$total_seconds")"
}

LOOP_START_TS="$(current_epoch_seconds)"

if ! command -v codex >/dev/null 2>&1; then
  log_error_printf '%s\n' '未检测到 codex 命令，请先安装并完成登录。'
  log_total_elapsed
  exit 1
fi

if [ ! -f "$PROMPT_FILE" ]; then
  log_error_printf '未找到 prompt 文件：%s\n' "$PROMPT_FILE"
  log_total_elapsed
  exit 1
fi

log_printf 'Starting Codex OpenSpec loop for %s\n' "$CHANGE_NAME"
log_printf 'Prompt: %s\n' "$PROMPT_FILE"
log_printf 'Max iterations: %s\n' "$MAX_ITERATIONS"
log_printf 'Model: %s\n' "$CODEX_MODEL"
log_printf 'Log file: %s\n' "$LOG_FILE"

for i in $(seq 1 "$MAX_ITERATIONS"); do
  log_printf '\n===============================================================\n'
  log_printf '  Codex Iteration %s of %s\n' "$i" "$MAX_ITERATIONS"
  log_printf '===============================================================\n'

  ITERATION_EXIT_CODE=0
  ITERATION_START_TS="$(current_epoch_seconds)"
  ITERATION_TMP_DIR="$(mktemp -d)"
  ITERATION_STDOUT_FILE="$ITERATION_TMP_DIR/stdout.log"
  ITERATION_STDERR_FILE="$ITERATION_TMP_DIR/stderr.log"

  # 只用 stdout 判定完成标记，避免 stderr 尾随告警或 tokens 统计干扰退出条件。
  env -u CODEX_THREAD_ID -u CODEX_INTERNAL_ORIGINATOR_OVERRIDE \
  codex exec \
    --ephemeral \
    --model "$CODEX_MODEL" \
    --dangerously-bypass-approvals-and-sandbox \
    --skip-git-repo-check \
    -C "$REPO_ROOT" \
    - < "$PROMPT_FILE" \
    > >(tee "$ITERATION_STDOUT_FILE" | tee -a "$LOG_FILE") \
    2> >(tee "$ITERATION_STDERR_FILE" | tee -a "$LOG_FILE" >&2) \
    || ITERATION_EXIT_CODE=$?

  ITERATION_END_TS="$(current_epoch_seconds)"
  ITERATION_ELAPSED_SECONDS=$((ITERATION_END_TS - ITERATION_START_TS))
  log_printf 'Iteration %s command exit code: %s\n' "$i" "$ITERATION_EXIT_CODE"
  log_printf 'Iteration %s elapsed: %s\n' "$i" "$(format_elapsed_seconds "$ITERATION_ELAPSED_SECONDS")"

  LAST_NONEMPTY_OUTPUT_LINE="$(
    cat "$ITERATION_STDOUT_FILE" \
      | sed -E 's/\x1B\[[0-9;]*[[:alpha:]]//g; s/\r$//' \
      | awk 'NF { line = $0 } END { print line }'
  )"

  rm -rf "$ITERATION_TMP_DIR"

  if [[ "$ITERATION_EXIT_CODE" -eq 0 && "$LAST_NONEMPTY_OUTPUT_LINE" == "<promise>COMPLETE</promise>" ]]; then
    log_printf '\nCodex completed all tasks at iteration %s.\n' "$i"
    log_total_elapsed
    exit 0
  fi

  log_printf 'Iteration %s complete. Continuing...\n' "$i"
  sleep "$SLEEP_SECONDS"
done

log_error_printf '\nCodex reached max iterations (%s) without completion.\n' "$MAX_ITERATIONS"
log_total_elapsed
exit 1
