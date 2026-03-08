#!/bin/bash

groupName=$1
hotfixName=$2

if [ -z "$groupName" ] || [ -z "$hotfixName" ]; then
  echo "Usage: $0 <groupName> <hotfixName>"
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

if [[ "$hotfixName" != "$hotfixPrefix"* ]]; then
  echo "Hotfix branch name must start with: $hotfixPrefix"
  exit 1
fi

if ! [[ "$hotfixVersion" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Hotfix version must follow SemVer format, e.g. 1.0.0"
  exit 1
fi

git fetch origin --prune >/dev/null 2>&1

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
echo "checkout branch: master"

git checkout "master"
git pull origin "master"
git checkout -b "$hotfixName"
git push --set-upstream origin "$hotfixName"

echo "hotfix branch created: $hotfixName"
