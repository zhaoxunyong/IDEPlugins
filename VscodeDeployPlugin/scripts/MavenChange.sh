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
mvnVersion=$2
NEXUS_BASE_URL="http://nexus.zerofinance.net"

git config pull.rebase false

if [ -z "$groupName" ] || [ -z "$mvnVersion" ]; then
  echo "Usage: $0 <groupName> <mavenVersion>"
  exit 1
fi

if [ ! -f "pom.xml" ]; then
  echo "当前目录不是 Maven 项目（缺少 pom.xml），流程已中断。"
  exit 1
fi

if ! [[ "$mvnVersion" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-SNAPSHOT|-RC[0-9]+)?$ ]]; then
  echo "Maven version must be x.y.z, x.y.z-SNAPSHOT or x.y.z-RCN (N为数字)"
  exit 1
fi

if [[ "$mvnVersion" =~ -SNAPSHOT$ ]]; then
  changeType="snapshot"
else
  changeType="release"
fi

if [ "$changeType" != "release" ] && [ "$changeType" != "snapshot" ]; then
  echo "Maven change type must be release or snapshot"
  exit 1
fi

echo "group name is: $groupName"
echo "target maven version is: $mvnVersion"
echo "maven change type is: $changeType"

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
  local groupId artifactId
  groupId=$(get_effective_maven_value "project.groupId")
  artifactId=$(get_effective_maven_value "project.artifactId")

  if [ -z "$groupId" ] || [ -z "$artifactId" ]; then
    echo "无法从 pom.xml 解析 groupId/artifactId，流程已中断。"
    return 2
  fi

  local queryUrl
  queryUrl="${NEXUS_BASE_URL}/service/local/lucene/search?g=${groupId}&a=${artifactId}&v=${mvnVersion}"
  echo "checking release version in nexus2: ${groupId}:${artifactId}:${mvnVersion}"

  local response
  response=$(curl -fsSL "$queryUrl" 2>/dev/null)
  if [ $? -ne 0 ] || [ -z "$response" ]; then
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

if [ "$changeType" = "release" ]; then
  check_release_version_exists_in_nexus
  releaseCheckResult=$?
  if [ "$releaseCheckResult" -ne 0 ]; then
    exit 1
  fi
fi

set +e
mvn versions:set -DnewVersion="${mvnVersion}"
setResult=$?
set -e

_VSDEP_MAIN_EXIT=1

if [ "$setResult" -eq 0 ]; then
  echo "mvn versions:set succeeded, committing versions..."
  mvn versions:commit
  echo "mvn versions:commit succeeded, starting mvn deploy..."
  set +e
  mvn deploy
  deployResult=$?
  set -e
  if [ "$deployResult" -eq 0 ]; then
    echo "MavenChange done."
    _VSDEP_MAIN_EXIT=0
  else
    echo "mvn deploy failed, please check logs and resolve manually."
  fi
else
  echo "mvn versions:set failed, reverting versions..."
  set +e
  mvn versions:revert
  revertResult=$?
  set -e

  if [ "$revertResult" -ne 0 ]; then
    echo "mvn versions:revert also failed. Please resolve manually."
  fi
fi

# 当前工作目录（$PWD）下若存在 Post_<本脚本文件名> 则执行，否则跳过（仅主流程成功时）
if [ "$_VSDEP_MAIN_EXIT" -eq 0 ]; then
  _VSDEP_POST="$PWD/Post_$(basename "${BASH_SOURCE[0]:-$0}")"
  if [ -f "$_VSDEP_POST" ]; then
    if [ -x "$_VSDEP_POST" ]; then
      "$_VSDEP_POST" "$@"
    else
      bash "$_VSDEP_POST" "$@"
    fi
  fi
  unset _VSDEP_POST
fi

_VSDEP_EXIT_CODE=$_VSDEP_MAIN_EXIT
unset _VSDEP_MAIN_EXIT
exit "$_VSDEP_EXIT_CODE"
