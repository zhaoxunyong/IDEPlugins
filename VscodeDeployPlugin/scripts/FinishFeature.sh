#!/bin/bash

groupName=$1

if [ -z "$groupName" ]; then
  echo "Usage: $0 <groupName>"
  exit 1
fi

set -e

developBranch="develop-$groupName"
featurePrefix="feature/$groupName/"
currentBranch=$(git branch --show-current)

if [ -z "$currentBranch" ]; then
  echo "Can not resolve current branch."
  exit 1
fi

if [[ "$currentBranch" != "$featurePrefix"* ]]; then
  echo "Current branch '$currentBranch' is not a feature branch of group '$groupName'."
  echo "Expected prefix: $featurePrefix"
  exit 1
fi

echo "Current feature branch: $currentBranch"
echo "Checkout branch: $developBranch"
git checkout "$developBranch"
git pull origin "$developBranch"
git branch -d "$currentBranch"
echo "Deleted local feature branch: $currentBranch"
