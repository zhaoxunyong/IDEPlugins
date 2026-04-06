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

groupName=$1
hotfixName=$2
baseTag=$3

if [ -z "$groupName" ] || [ -z "$hotfixName" ] || [ -z "$baseTag" ]; then
  echo "Usage: $0 <groupName> <hotfixName> <baseTag>"
  exit 1
fi

git config pull.rebase false

set -e

hotfixPrefix="hotfix/$groupName/"
hotfixVersion="${hotfixName#$hotfixPrefix}"
conflictReleaseName="release/$groupName/$hotfixVersion"

branch_exists() {
  local branchName=$1
  git show-ref --verify --quiet "refs/heads/$branchName" \
    || git show-ref --verify --quiet "refs/remotes/origin/$branchName"
}

remote_tag_exists() {
  local tagName=$1
  git ls-remote --tags --refs origin "refs/tags/$tagName" | grep -q .
}

if [[ "$hotfixName" != "$hotfixPrefix"* ]]; then
  echo "Hotfix branch name must start with: $hotfixPrefix"
  exit 1
fi

if ! [[ "$hotfixVersion" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Hotfix version must follow SemVer format, e.g. 1.0.0"
  exit 1
fi

git fetch origin --prune
git fetch origin --tags --prune

if ! remote_tag_exists "$baseTag"; then
  echo "Remote tag does not exist: $baseTag"
  exit 1
fi

if branch_exists "$hotfixName"; then
  echo "Branch already exists (local or remote): $hotfixName"
  exit 1
fi

if branch_exists "$conflictReleaseName"; then
  echo "Version conflict: $hotfixVersion already exists as $conflictReleaseName"
  exit 1
fi

echo "group name is: $groupName"
echo "hotfix name is: $hotfixName"
echo "base tag is: $baseTag"
echo "checkout tag: $baseTag"

git switch -c "$hotfixName" "$baseTag"
git push --set-upstream origin "$hotfixName"

echo "hotfix branch created: $hotfixName"

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
