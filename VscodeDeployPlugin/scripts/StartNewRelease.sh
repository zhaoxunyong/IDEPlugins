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
NEXUS_BASE_URL="http://nexus.zerofinance.net"

if [ -z "$groupName" ] || [ -z "$releaseName" ]; then
  echo "Usage: $0 <groupName> <releaseName>"
  exit 1
fi

set -e

developBranch="develop-$groupName"
releasePrefix="release/$groupName/"
releaseVersion="${releaseName#$releasePrefix}"
conflictHotfixName="hotfix/$groupName/$releaseVersion"
mvnReleaseVersion="${releaseVersion}-RC1"

branch_exists() {
  local branchName=$1
  git show-ref --verify --quiet "refs/heads/$branchName" \
    || git show-ref --verify --quiet "refs/remotes/origin/$branchName"
}

get_effective_maven_value() {
  local expr="$1"
  local result
  result=$(mvn -q help:evaluate -Dexpression="$expr" -DforceStdout 2>/dev/null | tail -n 1 | tr -d '\r')
  result="$(echo "$result" | xargs)"
  if [ -z "$result" ] || [ "$result" = "null object or invalid expression" ] || [[ "$result" == *"[ERROR]"* ]]; then
    return 1
  fi
  echo "$result"
  return 0
}

check_release_version_exists_in_nexus() {
  local mvnVersion="$1"
  if ! [[ "$mvnVersion" =~ ^[0-9]+\.[0-9]+\.[0-9]+-RC[0-9]+$ ]]; then
    echo "Maven version is not RC, skip nexus check: ${mvnVersion}"
    return 0
  fi

  local groupId artifactId
  if ! groupId=$(get_effective_maven_value "project.groupId") || ! artifactId=$(get_effective_maven_value "project.artifactId"); then
    echo "无法从 pom.xml 解析 groupId/artifactId，流程已中断。"
    return 2
  fi

  local queryUrl
  queryUrl="${NEXUS_BASE_URL}/service/local/lucene/search?g=${groupId}&a=${artifactId}&v=${mvnVersion}"
  echo "checking release version in nexus2: ${groupId}:${artifactId}:${mvnVersion}"

  local response
  if ! response=$(curl -fsSL "$queryUrl" 2>/dev/null) || [ -z "$response" ]; then
    echo "访问 Nexus2 失败，无法校验版本是否存在：${NEXUS_BASE_URL}"
    return 2
  fi

  local responseOneLine totalCount
  responseOneLine=$(echo "$response" | tr -d '\n\r')
  totalCount=$(echo "$responseOneLine" | sed -n 's:.*<totalCount>\([0-9]\+\)</totalCount>.*:\1:p')

  if [ -z "$totalCount" ]; then
    echo "Nexus2 返回格式异常，无法解析 totalCount，流程已中断。"
    return 2
  fi

  if [ "$totalCount" -gt 0 ]; then
    echo "Nexus 中已存在 release 版本：${groupId}:${artifactId}:${mvnVersion}"
    echo "请更换版本号后重试，流程已中断。"
    return 1
  fi

  echo "Nexus 未发现该 release 版本，可继续执行。"
  return 0
}

if [[ "$releaseName" != "$releasePrefix"* ]]; then
  echo "Release branch name must start with: $releasePrefix"
  exit 1
fi

if ! [[ "$releaseVersion" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Release version must follow SemVer format, e.g. 1.0.0"
  exit 1
fi

if [ -f "pom.xml" ]; then
  check_release_version_exists_in_nexus "$mvnReleaseVersion" || exit 1
fi

git config pull.rebase false

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
    echo "mvn versions:commit succeeded, starting mvn deploy..."
    mvn deploy
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
