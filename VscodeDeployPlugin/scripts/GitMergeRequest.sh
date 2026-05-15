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

groupName="${1:-}"
assignee="${2:-}"
target_branch="${3:-}"

if [[ -z "$groupName" ]]; then
  echo "Usage: $0 <groupName> [assignee] [target-branch]" >&2
  echo "  groupName: 与扩展 zerofinanceGit.groupNames 一致，用于默认目标分支 develop-<groupName>" >&2
  echo "  assignee:  GitLab 用户名，作为 push option merge_request.assign；可省略或留空表示不指定指派人" >&2
  echo "  target-branch: 可选，省略则为 develop-<groupName>" >&2
  exit 1
fi

require_command() {
  local name="$1"

  if ! command -v "$name" >/dev/null 2>&1; then
    echo "Command not found: $name" >&2
    exit 1
  fi
}

require_command git

EMPTY_COMMIT_MESSAGE="chore: trigger MR creation"

current_branch="$(git rev-parse --abbrev-ref HEAD)"
if [[ "$current_branch" == "HEAD" || -z "${current_branch//[$'\r\n\t ']}" ]]; then
  echo "Detached HEAD is not supported. Please checkout a branch first." >&2
  exit 1
fi

if [[ -z "$target_branch" ]]; then
  target_branch="develop-${groupName}"
fi

commit_message="$(git log -1 --format=%B)"

if [[ -z "${commit_message//[$'\r\n\t ']}" ]]; then
  echo "Failed to get the latest commit message" >&2
  exit 1
fi

title="$(printf '%s\n' "$commit_message" | awk 'NF { print; exit }')"

if [[ -z "${title//[$'\r\n\t ']}" ]]; then
  echo "The latest commit message is empty, cannot create MR" >&2
  exit 1
fi

ahead_behind="$(git rev-list --left-right --count @{u}...HEAD 2>/dev/null || true)"
ahead_count="$(printf '%s\n' "$ahead_behind" | awk '{ print $2 }')"
if [[ -n "${ahead_count//[$'\r\n\t ']}" && "$ahead_count" == "0" ]]; then
  echo "No outgoing commits detected. Creating an empty commit to trigger MR creation."
  git commit --allow-empty -m "$EMPTY_COMMIT_MESSAGE"
fi

echo "groupName: $groupName"
echo "Current branch: $current_branch"
echo "MR title from latest commit: $title"
echo "Target branch: $target_branch"
if [[ -n "${assignee//[$'\r\n\t ']}" ]]; then
  echo "Assignee: $assignee"
else
  echo "Assignee: (none)"
fi

push_args=(
  origin
  "HEAD:${current_branch}"
  -o merge_request.create
  -o "merge_request.target=${target_branch}"
  -o "merge_request.title=${title}"
)
if [[ -n "${assignee//[$'\r\n\t ']}" ]]; then
  push_args+=(-o "merge_request.assign=${assignee}")
fi

git push "${push_args[@]}"

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
