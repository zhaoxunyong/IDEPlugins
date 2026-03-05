#!/bin/bash

groupName=$1
hotfixName=$2

if [ -z "$groupName" ] || [ -z "$hotfixName" ]; then
  echo "Usage: $0 <groupName> <hotfixName>"
  exit 1
fi

set -e

hotfixPrefix="hotfix/$groupName/"
hotfixVersion="${hotfixName#$hotfixPrefix}"

if [[ "$hotfixName" != "$hotfixPrefix"* ]]; then
  echo "Hotfix branch name must start with: $hotfixPrefix"
  exit 1
fi

if ! [[ "$hotfixVersion" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Hotfix version must follow SemVer format, e.g. 1.0.0"
  exit 1
fi

echo "group name is: $groupName"
echo "hotfix name is: $hotfixName"
echo "checkout branch: master"

git checkout "master"
git pull origin "master"
git checkout -b "$hotfixName"
git push origin "$hotfixName"

echo "hotfix branch created: $hotfixName"
