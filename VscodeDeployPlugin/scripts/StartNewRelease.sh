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
releaseName=$2

if [ -z "$groupName" ] || [ -z "$releaseName" ]; then
  echo "Usage: $0 <groupName> <releaseName>"
  exit 1
fi

git config pull.rebase false

set -e

developBranch="develop-$groupName"
releasePrefix="release/$groupName/"
releaseVersion="${releaseName#$releasePrefix}"
conflictHotfixName="hotfix/$groupName/$releaseVersion"

branch_exists() {
  local branchName=$1
  git show-ref --verify --quiet "refs/heads/$branchName" \
    || git show-ref --verify --quiet "refs/remotes/origin/$branchName"
}

if [[ "$releaseName" != "$releasePrefix"* ]]; then
  echo "Release branch name must start with: $releasePrefix"
  exit 1
fi

if ! [[ "$releaseVersion" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Release version must follow SemVer format, e.g. 1.0.0"
  exit 1
fi

git fetch origin --prune >/dev/null 2>&1

if branch_exists "$releaseName"; then
  echo "Branch already exists (local or remote): $releaseName"
  exit 1
fi

if branch_exists "$conflictHotfixName"; then
  echo "Version conflict: $releaseVersion already exists as $conflictHotfixName"
  exit 1
fi

echo "group name is: $groupName"
echo "release name is: $releaseName"
echo "checkout branch: $developBranch"

git checkout "$developBranch"
git pull origin "$developBranch"
git checkout -b "$releaseName"


if [ -f "pom.xml" ]; then
  mvnReleaseVersion="${releaseVersion}-RC1"
  echo "Maven project detected, updating pom version to: $mvnReleaseVersion"
  set +e
  mvn -q versions:set -DnewVersion="${mvnReleaseVersion}"
  setResult=$?
  set -e
  if [ "$setResult" -eq 0 ]; then
    mvn -q versions:commit
    if [ -n "$(git status --porcelain)" ]; then
      git add -A
      git commit -m "chore: set version to ${mvnReleaseVersion}"
    fi
  else
    echo "mvn versions:set failed, reverting..."
    set +e
    mvn -q versions:revert
    set -e
    echo "Release branch created but pom version not updated. Please fix manually."
    exit 1
  fi
fi

git push --set-upstream origin "$releaseName"

echo "release branch created: $releaseName"

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
