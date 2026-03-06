#!/bin/bash

groupName=$1
mvnVersion=$2

if [ -z "$groupName" ] || [ -z "$mvnVersion" ]; then
  echo "Usage: $0 <groupName> <mavenVersion>"
  exit 1
fi

if [ ! -f "pom.xml" ]; then
  echo "当前目录不是 Maven 项目（缺少 pom.xml），流程已中断。"
  exit 1
fi

if ! [[ "$mvnVersion" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-SNAPSHOT)?$ ]]; then
  echo "Maven version must be x.y.z or x.y.z-SNAPSHOT"
  exit 1
fi

echo "group name is: $groupName"
echo "target maven version is: $mvnVersion"

set +e
mvn versions:set -DnewVersion="${mvnVersion}"
setResult=$?
set -e

if [ "$setResult" -eq 0 ]; then
  echo "mvn versions:set succeeded, committing versions..."
  mvn versions:commit
  echo "MavenChange done."
  exit 0
fi

echo "mvn versions:set failed, reverting versions..."
set +e
mvn versions:revert
revertResult=$?
set -e

if [ "$revertResult" -ne 0 ]; then
  echo "mvn versions:revert also failed. Please resolve manually."
fi

exit 1
