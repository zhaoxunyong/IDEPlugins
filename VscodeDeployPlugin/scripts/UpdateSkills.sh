#!/usr/bin/env bash

# 从 skills-templates 更新选中的全局/项目级 skills。
# 约定参数：--global skill-a skill-b --project skill-c skill-d
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

usage() {
  printf '用法: %s [--global skill...] [--project skill...]\n' "$(basename "$0")" >&2
}

validate_skill_name() {
  local skill="$1"
  [[ "$skill" =~ ^[a-zA-Z0-9][a-zA-Z0-9._-]*$ ]] ||
    fail "非法 skill 名称: $skill"
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

GLOBAL_SKILLS=()
PROJECT_SKILLS=()
scope=''
for arg in "$@"; do
  case "$arg" in
    --global)
      scope='global'
      ;;
    --project)
      scope='project'
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
      [[ -n "$scope" ]] || {
        usage
        fail "skill 必须放在 --global 或 --project 后面: $arg"
      }
      validate_skill_name "$arg"
      if [[ "$scope" == 'global' ]]; then
        contains_skill "$arg" "${GLOBAL_SKILLS[@]}" || GLOBAL_SKILLS+=("$arg")
      else
        contains_skill "$arg" "${PROJECT_SKILLS[@]}" || PROJECT_SKILLS+=("$arg")
      fi
      ;;
  esac
done

if (( ${#GLOBAL_SKILLS[@]} + ${#PROJECT_SKILLS[@]} == 0 )); then
  usage
  fail '没有选择任何 skill'
fi

[[ "$PROJECT_ROOT" != '/' ]] || fail '项目根目录不能是 /'

TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/update-skills.XXXXXX")"
cleanup() {
  rm -rf -- "$TMP_ROOT"
}
trap cleanup EXIT

SOURCE_ROOT="$TMP_ROOT/source"
log "克隆 skills 源仓库: $SOURCE_REPO"
git clone --depth 1 "$SOURCE_REPO" "$SOURCE_ROOT" >/dev/null ||
  fail '无法下载 skills 源仓库'

ALL_SKILLS=("${GLOBAL_SKILLS[@]}" "${PROJECT_SKILLS[@]}")
for skill in "${ALL_SKILLS[@]}"; do
  [[ -d "$SOURCE_ROOT/skills/$skill" ]] ||
    fail "源仓库中不存在 skill: $skill"
done

TARGET_DESTS=()
TARGET_STAGES=()
TARGET_BACKUPS=()

prepare_scope() {
  local scope_name="$1"
  local skills_root="$2"
  local backup_parent="$3"
  shift 3
  local selected=("$@")
  local backup_root=''
  local needs_backup=0
  local skill dest

  (( ${#selected[@]} > 0 )) || return 0
  for skill in "${selected[@]}"; do
    dest="$skills_root/$skill"
    if [[ -e "$dest" || -L "$dest" ]]; then
      needs_backup=1
    fi
  done
  if (( needs_backup )); then
    mkdir -p "$backup_parent"
    backup_root="$(mktemp -d "$backup_parent/$(date +%Y%m%d%H%M%S)-$scope_name-XXXXXX")"
    log "$scope_name skills 备份目录: $backup_root"
  fi

  for skill in "${selected[@]}"; do
    dest="$skills_root/$skill"
    local stage="$TMP_ROOT/staged/$scope_name/$skill"
    mkdir -p "$stage"
    cp -a "$SOURCE_ROOT/skills/$skill/." "$stage/"
    TARGET_DESTS+=("$dest")
    TARGET_STAGES+=("$stage")
    if [[ -n "$backup_root" && ( -e "$dest" || -L "$dest" ) ]]; then
      TARGET_BACKUPS+=("$backup_root/$skill")
    else
      TARGET_BACKUPS+=('')
    fi
  done
}

prepare_scope global "$GLOBAL_SKILLS_ROOT" "$BACKUP_ROOT" "${GLOBAL_SKILLS[@]}"
prepare_scope project "$PROJECT_SKILLS_ROOT" "$BACKUP_ROOT" "${PROJECT_SKILLS[@]}"

APPLIED_COUNT=0
rollback() {
  local i dest backup
  log '更新失败，开始从备份回滚'
  for (( i=APPLIED_COUNT - 1; i >= 0; i-- )); do
    dest="${TARGET_DESTS[$i]}"
    backup="${TARGET_BACKUPS[$i]}"
    rm -rf -- "$dest" || true
    if [[ -n "$backup" && ( -e "$backup" || -L "$backup" ) ]]; then
      mv -- "$backup" "$dest" || true
    fi
  done
}

for (( i = 0; i < ${#TARGET_DESTS[@]}; i++ )); do
  dest="${TARGET_DESTS[$i]}"
  stage="${TARGET_STAGES[$i]}"
  backup="${TARGET_BACKUPS[$i]}"
  if ! mkdir -p "$(dirname "$dest")"; then
    rollback
    fail "无法创建 skill 目录: $(dirname "$dest")"
  fi

  if [[ -n "$backup" ]]; then
    if ! mkdir -p "$(dirname "$backup")"; then
      rollback
      fail "无法创建备份目录: $(dirname "$backup")"
    fi
    if ! mv -- "$dest" "$backup"; then
      rollback
      fail "无法备份旧 skill: $dest"
    fi
    log "已备份: $dest -> $backup"
  fi

  if ! mv -- "$stage" "$dest"; then
    if [[ -n "$backup" && ( -e "$backup" || -L "$backup" ) ]]; then
      mv -- "$backup" "$dest" || true
    fi
    rollback
    fail "无法安装新 skill: $dest"
  fi
  (( APPLIED_COUNT += 1 ))
  log "已更新: $dest"
done

log '更新完成；未选择的 skills 未做任何删除或修改'
