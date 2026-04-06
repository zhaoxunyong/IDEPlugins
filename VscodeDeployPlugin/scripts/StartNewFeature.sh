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
featureName=$2

if [ -z "$groupName" ] || [ -z "$featureName" ]; then
  echo "Usage: $0 <groupName> <featureName>"
  exit 1
fi

git config pull.rebase false

set -e

developBranch="develop-$groupName"

echo "group name is: $groupName"
echo "feature name is: $featureName"
echo "checkout branch: $developBranch"

git checkout "$developBranch"
git pull origin "$developBranch"
git checkout -b "$featureName"

#git push --set-upstream origin "$featureName"
echo "feature branch created locally (no auto push): $featureName"

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
