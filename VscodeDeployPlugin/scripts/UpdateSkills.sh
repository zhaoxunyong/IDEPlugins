#!/usr/bin/env bash

# 按 GetSkills.sh 配置更新或备份删除全局/项目级 skills。
set -Eeuo pipefail

SOURCE_REPO="${SKILLS_TEMPLATE_REPO:-http://gitlab.zerofinance.net/commons/skills-templates.git}"
PROJECT_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd -P)"
GLOBAL_SKILLS_ROOT="${HOME:?}/.agents/skills"
PROJECT_SKILLS_ROOT="$PROJECT_ROOT/.agents/skills"
BACKUP_ROOT="${TMPDIR:-/tmp}/zerogit-skill-backups"

log() {
  printf '[UpdateSkills][debug] %s\n' "$*"
}

fail() {
  printf '[UpdateSkills][error] %s\n' "$*" >&2
  exit 1
}

warn() {
  printf '[UpdateSkills][warn] %s\n' "$*" >&2
}

usage() {
  printf '用法: %s [--update-global skill...] [--update-project skill...] [--delete-global skill...] [--delete-project skill...]\n' "$(basename "$0")" >&2
}

validate_skill_name() {
  local skill="$1"
  [[ "$skill" =~ ^[a-zA-Z0-9][a-zA-Z0-9._-]*$ ]] || fail "非法 skill 名称: $skill"
}

contains_skill() {
  local candidate="$1"
  shift
  local existing
  for existing in "$@"; do
    [[ "$existing" == "$candidate" ]] && return 0
  done
  return 1
}

UPDATE_GLOBAL_SKILLS=()
UPDATE_PROJECT_SKILLS=()
DELETE_GLOBAL_SKILLS=()
DELETE_PROJECT_SKILLS=()
group=''
group_skill_count=0
for arg in "$@"; do
  case "$arg" in
    --update-global|--update-project|--delete-global|--delete-project)
      [[ -z "$group" || $group_skill_count -gt 0 ]] || fail "参数 --$group 后缺少 skill"
      group="${arg#--}"
      group_skill_count=0
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    --*)
      usage
      fail "未知参数: $arg"
      ;;
    *)
      [[ -n "$group" ]] || {
        usage
        fail "skill 必须放在动作参数后面: $arg"
      }
      validate_skill_name "$arg"
      (( group_skill_count += 1 ))
      case "$group" in
        update-global)
          contains_skill "$arg" "${UPDATE_GLOBAL_SKILLS[@]}" || UPDATE_GLOBAL_SKILLS+=("$arg")
          ;;
        update-project)
          contains_skill "$arg" "${UPDATE_PROJECT_SKILLS[@]}" || UPDATE_PROJECT_SKILLS+=("$arg")
          ;;
        delete-global)
          contains_skill "$arg" "${DELETE_GLOBAL_SKILLS[@]}" || DELETE_GLOBAL_SKILLS+=("$arg")
          ;;
        delete-project)
          contains_skill "$arg" "${DELETE_PROJECT_SKILLS[@]}" || DELETE_PROJECT_SKILLS+=("$arg")
          ;;
      esac
      ;;
  esac
done
[[ -z "$group" || $group_skill_count -gt 0 ]] || fail "参数 --$group 后缺少 skill"

TOTAL_SKILLS=$(( ${#UPDATE_GLOBAL_SKILLS[@]} + ${#UPDATE_PROJECT_SKILLS[@]} + ${#DELETE_GLOBAL_SKILLS[@]} + ${#DELETE_PROJECT_SKILLS[@]} ))
(( TOTAL_SKILLS > 0 )) || {
  usage
  fail '没有选择任何 skill'
}
[[ "$PROJECT_ROOT" != '/' ]] || fail '项目根目录不能是 /'

VALID_DELETE_GLOBAL_SKILLS=()
for skill in "${DELETE_GLOBAL_SKILLS[@]}"; do
  if [[ -e "$GLOBAL_SKILLS_ROOT/$skill" || -L "$GLOBAL_SKILLS_ROOT/$skill" ]]; then
    VALID_DELETE_GLOBAL_SKILLS+=("$skill")
  else
    warn "待删除的 skill 不存在，已跳过: $GLOBAL_SKILLS_ROOT/$skill"
  fi
done
DELETE_GLOBAL_SKILLS=("${VALID_DELETE_GLOBAL_SKILLS[@]}")
VALID_DELETE_PROJECT_SKILLS=()
for skill in "${DELETE_PROJECT_SKILLS[@]}"; do
  if [[ -e "$PROJECT_SKILLS_ROOT/$skill" || -L "$PROJECT_SKILLS_ROOT/$skill" ]]; then
    VALID_DELETE_PROJECT_SKILLS+=("$skill")
  else
    warn "待删除的 skill 不存在，已跳过: $PROJECT_SKILLS_ROOT/$skill"
  fi
done
DELETE_PROJECT_SKILLS=("${VALID_DELETE_PROJECT_SKILLS[@]}")

for skill in "${UPDATE_GLOBAL_SKILLS[@]}"; do
  contains_skill "$skill" "${DELETE_GLOBAL_SKILLS[@]}" && fail "同一作用域的 skill 不能同时更新和删除: $skill"
done
for skill in "${UPDATE_PROJECT_SKILLS[@]}"; do
  contains_skill "$skill" "${DELETE_PROJECT_SKILLS[@]}" && fail "同一作用域的 skill 不能同时更新和删除: $skill"
done

TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/update-skills.XXXXXX")"
cleanup() {
  rm -rf -- "$TMP_ROOT"
}
trap cleanup EXIT

UPDATE_COUNT=$(( ${#UPDATE_GLOBAL_SKILLS[@]} + ${#UPDATE_PROJECT_SKILLS[@]} ))
SOURCE_ROOT="$TMP_ROOT/source"
if (( UPDATE_COUNT > 0 )); then
  log "克隆 skills 源仓库: $SOURCE_REPO"
  git clone -q --depth 1 "$SOURCE_REPO" "$SOURCE_ROOT" || fail '无法下载 skills 源仓库'
  VALID_UPDATE_GLOBAL_SKILLS=()
  for skill in "${UPDATE_GLOBAL_SKILLS[@]}"; do
    if [[ -d "$SOURCE_ROOT/skills/$skill" ]]; then
      VALID_UPDATE_GLOBAL_SKILLS+=("$skill")
    else
      warn "源仓库中不存在 skill，已跳过: $skill"
    fi
  done
  UPDATE_GLOBAL_SKILLS=("${VALID_UPDATE_GLOBAL_SKILLS[@]}")
  VALID_UPDATE_PROJECT_SKILLS=()
  for skill in "${UPDATE_PROJECT_SKILLS[@]}"; do
    if [[ -d "$SOURCE_ROOT/skills/$skill" ]]; then
      VALID_UPDATE_PROJECT_SKILLS+=("$skill")
    else
      warn "源仓库中不存在 skill，已跳过: $skill"
    fi
  done
  UPDATE_PROJECT_SKILLS=("${VALID_UPDATE_PROJECT_SKILLS[@]}")
  for skill in "${UPDATE_GLOBAL_SKILLS[@]}" "${UPDATE_PROJECT_SKILLS[@]}"; do
    [[ -d "$SOURCE_ROOT/skills/$skill" ]] || fail "源仓库中不存在 skill: $skill"
  done
fi
TOTAL_SKILLS=$(( ${#UPDATE_GLOBAL_SKILLS[@]} + ${#UPDATE_PROJECT_SKILLS[@]} + ${#DELETE_GLOBAL_SKILLS[@]} + ${#DELETE_PROJECT_SKILLS[@]} ))
(( TOTAL_SKILLS > 0 )) || {
  warn '没有可执行的 skill，已跳过'
  exit 0
}

mkdir -p "$BACKUP_ROOT"
RUN_BACKUP_ROOT="$(mktemp -d "$BACKUP_ROOT/$(date +%Y%m%d%H%M%S)-XXXXXX")"
TARGET_ACTIONS=()
TARGET_DESTS=()
TARGET_STAGES=()
TARGET_BACKUPS=()

queue_targets() {
  local action="$1" scope="$2" skills_root="$3"
  shift 3
  local skill dest stage backup
  for skill in "$@"; do
    dest="$skills_root/$skill"
    stage=''
    if [[ "$action" == update ]]; then
      stage="$TMP_ROOT/staged/$scope/$skill"
      mkdir -p "$stage"
      cp -a "$SOURCE_ROOT/skills/$skill/." "$stage/"
    fi
    backup=''
    if [[ -e "$dest" || -L "$dest" ]]; then
      backup="$RUN_BACKUP_ROOT/$scope/$skill"
    fi
    TARGET_ACTIONS+=("$action")
    TARGET_DESTS+=("$dest")
    TARGET_STAGES+=("$stage")
    TARGET_BACKUPS+=("$backup")
  done
}

queue_targets update global "$GLOBAL_SKILLS_ROOT" "${UPDATE_GLOBAL_SKILLS[@]}"
queue_targets delete global "$GLOBAL_SKILLS_ROOT" "${DELETE_GLOBAL_SKILLS[@]}"
queue_targets update project "$PROJECT_SKILLS_ROOT" "${UPDATE_PROJECT_SKILLS[@]}"
queue_targets delete project "$PROJECT_SKILLS_ROOT" "${DELETE_PROJECT_SKILLS[@]}"

APPLIED_COUNT=0
rollback() {
  local i dest backup
  log '执行失败，开始从备份逆序回滚'
  for (( i=APPLIED_COUNT - 1; i >= 0; i-- )); do
    dest="${TARGET_DESTS[$i]}"
    backup="${TARGET_BACKUPS[$i]}"
    rm -rf -- "$dest" || true
    if [[ -n "$backup" && ( -e "$backup" || -L "$backup" ) ]]; then
      mkdir -p "$(dirname "$dest")" || true
      mv -- "$backup" "$dest" || true
    fi
  done
}

for (( i=0; i < ${#TARGET_DESTS[@]}; i++ )); do
  action="${TARGET_ACTIONS[$i]}"
  dest="${TARGET_DESTS[$i]}"
  stage="${TARGET_STAGES[$i]}"
  backup="${TARGET_BACKUPS[$i]}"
  if ! mkdir -p "$(dirname "$dest")"; then
    rollback
    fail "无法创建 skill 目录: $(dirname "$dest")"
  fi
  if [[ -n "$backup" ]]; then
    if ! mkdir -p "$(dirname "$backup")" || ! mv -- "$dest" "$backup"; then
      rollback
      fail "无法备份旧 skill: $dest"
    fi
  fi
  if [[ "$action" == update ]] && ! mv -- "$stage" "$dest"; then
    if [[ -n "$backup" && ( -e "$backup" || -L "$backup" ) ]]; then
      mv -- "$backup" "$dest" || true
    fi
    rollback
    fail "无法安装新 skill: $dest"
  fi
  (( APPLIED_COUNT += 1 ))
  log "已$([[ "$action" == update ]] && printf '更新' || printf '删除'): $dest"
done

log "执行完成；备份位置: $RUN_BACKUP_ROOT"
