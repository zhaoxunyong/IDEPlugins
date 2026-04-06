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

export PATH="/usr/local/bin:/usr/bin:$JAVA_HOME/bin:$MVN_HOME/bin:$PATH"

git config pull.rebase false

fail() {
  echo "$1"
  exit 1
}

# 1) Working tree must be clean (staged/unstaged/untracked all blocked).
if [[ -n "$(git status --porcelain)" ]]; then
  fail "Your local repo has uncommitted changes, please commit or stash them first!"
fi

currentBranchVersion="$(git rev-parse --abbrev-ref HEAD 2>/dev/null)"
if [[ -z "$currentBranchVersion" || "$currentBranchVersion" == "HEAD" ]]; then
  fail "Cannot detect current branch. Please checkout a branch first."
fi

# Ensure the branch tracks an upstream branch.
if ! git rev-parse --abbrev-ref --symbolic-full-name "@{u}" >/dev/null 2>&1; then
  fail "Current branch \"$currentBranchVersion\" has no upstream branch. Please set upstream first."
fi

# 2) Sync refs from remote before comparing divergence.
git fetch origin --prune >/dev/null 2>&1 || fail "Failed to fetch from origin."

# behind = commits only in upstream (need pull), ahead = commits only in local (need push).
read -r behind ahead <<< "$(git rev-list --left-right --count "@{u}...HEAD" 2>/dev/null)"
if [[ -z "$behind" || -z "$ahead" ]]; then
  fail "Failed to compare local and upstream branch."
fi

if (( behind > 0 )); then
  fail "Your local repo seems out of date, please \"git pull\" first!"
fi

if (( ahead > 0 )); then
  fail "Your local repo has unpushed commits, please \"git push\" first!"
fi

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
