#!/bin/bash

groupName=$1
featureName=$2

if [ -z "$groupName" ] || [ -z "$featureName" ]; then
  echo "Usage: $0 <groupName> <featureName>"
  exit 1
fi

git config user.email "dev@zerofinance.com"
git config user.name "ai-dev"
git config pull.rebase false

set -e

developBranch="develop-$groupName"

echo "group name is: $groupName"
echo "feature name is: $featureName"
echo "checkout branch: $developBranch"

git checkout "$developBranch"
git pull origin "$developBranch"
git checkout -b "$featureName"
git push --set-upstream origin "$featureName"

echo "feature branch created: $featureName"
